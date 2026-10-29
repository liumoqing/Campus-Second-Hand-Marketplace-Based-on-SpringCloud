package com.campus.mall.order.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.order.client.MessageClient;
import com.campus.mall.order.client.ProductClient;
import com.campus.mall.order.client.UserClient;
import com.campus.mall.order.dto.OrderCreateDTO;
import com.campus.mall.order.dto.OrderQuery;
import com.campus.mall.order.entity.Order;
import com.campus.mall.order.entity.OrderItem;
import com.campus.mall.order.entity.OrderStatusLog;
import com.campus.mall.order.mapper.OrderItemMapper;
import com.campus.mall.order.mapper.OrderMapper;
import com.campus.mall.order.mapper.OrderStatusLogMapper;
import com.campus.mall.order.mapper.ReviewMapper;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 订单主流程（O1~O5、O19~O22）。
 *
 * <p>下单是唯一跨库的写操作：mall-product 扣库存 + mall-order 建单，用 Seata AT 的
 * {@code @GlobalTransactional} 包起来（01 §7.6）；退款不走全局事务（payment 本地改单 + 消息补偿）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** 运费：包邮 0 / 同城 5 / 跨城 10 / 自提 0（01 BR-09） */
    private static final BigDecimal FREIGHT_SAME_CITY = new BigDecimal("5.00");
    private static final BigDecimal FREIGHT_CROSS_CITY = new BigDecimal("10.00");

    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderStatusLogMapper logMapper;
    private final ReviewMapper reviewMapper;
    private final ProductClient productClient;
    private final UserClient userClient;
    private final MessageClient messageClient;

    // ---------------- O1 下单 ----------------

    @GlobalTransactional(name = "mall-create-order", rollbackFor = Exception.class)
    public Map<String, Object> create(Long buyerId, OrderCreateDTO dto) {
        Map<String, Object> product = viewProduct(dto.getProductId());
        Long sellerId = longOf(product.get("sellerId"));
        String productStatus = str(product.get("status"));
        if (Objects.equals(sellerId, buyerId)) {
            throw new BizException(ErrorCode.ORDER_SELF_PRODUCT);
        }
        if (!"ON_SALE".equals(productStatus)) {
            throw new BizException(ErrorCode.PRODUCT_UNAVAILABLE);
        }
        BigDecimal price = priceOf(product);
        String productCity = str(product.get("city"));
        String freightMode = str(product.get("freightMode"));

        Map<String, Object> address = address(buyerId, dto.getAddressId());
        String addressCity = str(address.get("city"));
        boolean sameCity = productCity != null && productCity.equals(addressCity);
        BigDecimal freight = calcFreight(freightMode, sameCity);

        String orderNo = genOrderNo();
        BigDecimal payAmount = price.add(freight);

        // 分支 1：mall-product 扣库存并置 LOCKED（条件 UPDATE + version，幂等靠 lock_order_no）
        productClient.deduct(new ProductClient.StockBody(dto.getProductId(), orderNo));

        // 分支 2：mall-order 建单
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setBuyerId(buyerId);
        order.setSellerId(sellerId);
        order.setProductId(dto.getProductId());
        order.setTotalAmount(price);
        order.setFreight(freight);
        order.setFreightMode(freightMode == null ? "DISTANCE" : freightMode);
        order.setSameCity(sameCity ? 1 : 0);
        order.setPayAmount(payAmount);
        order.setStatus("UNPAID");
        order.setRemark(dto.getRemark());
        order.setAddressSnapshot(toJson(address));
        order.setBuyerReviewed(0);
        order.setSellerReviewed(0);
        order.setVersion(0);
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(dto.getProductId());
        item.setProductTitle(str(product.get("title")));
        item.setProductCover(str(product.get("coverUrl")));
        item.setPrice(price);
        item.setQuantity(1);
        item.setCreateTime(LocalDateTime.now());
        itemMapper.insert(item);

        logStatus(order.getId(), null, "UNPAID", buyerId, "BUYER", "买家下单");

        // 卡片提醒：买家→卖家（失败只记日志，不影响下单）
        sendCard(orderNo, "ORDER_CREATED", buyerId, sellerId, dto.getProductId(),
                str(product.get("title")), "买家已下单，等待付款", "查看订单");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("totalAmount", price);
        data.put("freight", freight);
        data.put("payAmount", payAmount);
        data.put("status", order.getStatus());
        data.put("sameCity", sameCity);
        return data;
    }

    /** 运费规则：包邮/自提 0（自提跨城直接拒），按距离同城 5 跨城 10 */
    private BigDecimal calcFreight(String mode, boolean sameCity) {
        if ("FREE_SHIPPING".equals(mode) || "SELF_PICKUP".equals(mode)) {
            if ("SELF_PICKUP".equals(mode) && !sameCity) {
                throw new BizException(ErrorCode.ORDER_CROSS_CITY_PICKUP);
            }
            return BigDecimal.ZERO;
        }
        return sameCity ? FREIGHT_SAME_CITY : FREIGHT_CROSS_CITY;
    }

    // ---------------- O2 列表 / O3 详情 ----------------

    public PageResult<Map<String, Object>> page(Long userId, OrderQuery q) {
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<>();
        if ("sold".equals(q.getRole())) {
            w.eq(Order::getSellerId, userId);
        } else if ("all".equals(q.getRole())) {
            w.and(x -> x.eq(Order::getBuyerId, userId).or().eq(Order::getSellerId, userId));
        } else {
            w.eq(Order::getBuyerId, userId);
        }
        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            w.eq(Order::getStatus, q.getStatus());
        }
        w.orderByDesc(Order::getCreateTime);
        Page<Order> page = orderMapper.selectPage(new Page<>(Math.max(1, q.getPageNum()), Math.min(50, Math.max(1, q.getPageSize()))), w);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order o : page.getRecords()) {
            rows.add(card(o, userId));
        }
        return PageResult.from(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Map<String, Object> detail(Long userId, String orderNo) {
        Order o = requireOrder(orderNo);
        if (!Objects.equals(o.getBuyerId(), userId) && !Objects.equals(o.getSellerId(), userId)
                && !com.campus.mall.common.security.UserContext.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        Map<String, Object> data = card(o, userId);
        OrderItem item = itemOf(o.getId());
        if (item != null) {
            data.put("productTitle", item.getProductTitle());
            data.put("productCover", item.getProductCover());
            data.put("price", item.getPrice());
            data.put("quantity", item.getQuantity());
        }
        data.put("address", addressOf(o));
        data.put("payType", o.getPayType());
        data.put("freightMode", o.getFreightMode());
        data.put("sameCity", o.getSameCity() != null && o.getSameCity() == 1);
        data.put("logisticsNo", o.getLogisticsNo());
        data.put("remark", o.getRemark());
        data.put("payTime", o.getPayTime());
        data.put("shipTime", o.getShipTime());
        data.put("receiveTime", o.getReceiveTime());
        data.put("finishTime", o.getFinishTime());
        data.put("cancelTime", o.getCancelTime());
        data.put("cancelReason", o.getCancelReason());
        data.put("buyerReviewed", o.getBuyerReviewed() != null && o.getBuyerReviewed() == 1);
        data.put("sellerReviewed", o.getSellerReviewed() != null && o.getSellerReviewed() == 1);
        // 支付倒计时：服务端算好 expireTime，前端只显示（03 O3）
        if ("UNPAID".equals(o.getStatus())) {
            data.put("expireTime", o.getCreateTime().plusMinutes(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        data.put("timeline", timeline(o.getId()));
        return data;
    }

    // ---------------- O4 取消 / O5 发货 / O6 收货 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancel(Long userId, String orderNo, String reason) {
        Order o = requireOrder(orderNo);
        if (!Objects.equals(o.getBuyerId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = orderMapper.cancelUnpaid(orderNo, now, reason == null ? "买家主动取消" : reason);
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "只有待付款订单可以取消");
        }
        logStatus(o.getId(), "UNPAID", "CANCELLED", userId, "BUYER", reason);
        safeRestore(o.getProductId(), orderNo);
        sendCard(orderNo, "ORDER_CANCELLED", o.getBuyerId(), o.getSellerId(), o.getProductId(),
                null, "买家已取消订单", "查看订单");
        return Map.of("orderNo", orderNo, "status", "CANCELLED");
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ship(Long sellerId, String orderNo, String logisticsNo) {
        Order o = requireOrder(orderNo);
        if (!Objects.equals(o.getSellerId(), sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = orderMapper.ship(orderNo, now, logisticsNo);
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "只有待发货订单可以发货");
        }
        logStatus(o.getId(), "UNSHIPPED", "UNRECEIVED", sellerId, "SELLER", "卖家发货 " + (logisticsNo == null ? "" : logisticsNo));
        sendCard(orderNo, "ORDER_SHIPPED", o.getSellerId(), o.getBuyerId(), o.getProductId(),
                null, "卖家已发货，请注意查收", "查看订单");
        return Map.of("orderNo", orderNo, "status", "UNRECEIVED");
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> receive(Long buyerId, String orderNo) {
        Order o = requireOrder(orderNo);
        if (!Objects.equals(o.getBuyerId(), buyerId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = orderMapper.receive(orderNo, now);
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "只有待收货订单可以确认收货");
        }
        logStatus(o.getId(), "UNRECEIVED", "COMPLETED", buyerId, "BUYER", "买家确认收货");
        sendCard(orderNo, "ORDER_RECEIVED", buyerId, o.getSellerId(), o.getProductId(),
                null, "买家已确认收货，交易完成", "去评价");
        return Map.of("orderNo", orderNo, "status", "COMPLETED");
    }

    /** O20 支付成功回调（payment 同步调用；MQ 兜底也走这里） */
    @Transactional(rollbackFor = Exception.class)
    public boolean notifyPaid(String orderNo) {
        Order o = requireOrder(orderNo);
        LocalDateTime now = LocalDateTime.now();
        int rows = orderMapper.markPaid(orderNo, "MOCK", now);
        if (rows == 0) {
            if ("UNSHIPPED".equals(o.getStatus())) {
                log.info("订单已支付，幂等返回：{}", orderNo);
                return true;
            }
            throw new BizException(ErrorCode.PAY_STATUS_NOT_ALLOWED);
        }
        logStatus(o.getId(), "UNPAID", "UNSHIPPED", null, "SYSTEM", "模拟支付成功");
        // 商品置 SOLD（钱付了就是卖掉了），失败不回滚订单
        try {
            productClient.sold(new ProductClient.StockBody(o.getProductId(), orderNo));
        } catch (Exception e) {
            log.warn("置商品售出失败，等补偿任务：orderNo={}, err={}", orderNo, e.getMessage());
        }
        sendCard(orderNo, "ORDER_PAID", o.getBuyerId(), o.getSellerId(), o.getProductId(),
                null, "买家已付款，请尽快发货", "去发货");
        return true;
    }

    /** O21 退款完成回调（payment 调用），用于订单状态收口 */
    @Transactional(rollbackFor = Exception.class)
    public boolean notifyRefunded(String orderNo) {
        Order o = requireOrder(orderNo);
        int rows = orderMapper.refunded(orderNo, LocalDateTime.now());
        if (rows == 0) {
            log.info("订单已是终态，忽略退款回调：{} / {}", orderNo, o.getStatus());
            return true;
        }
        logStatus(o.getId(), "REFUNDING", "REFUNDED", null, "SYSTEM", "退款完成");
        return true;
    }

    // ---------------- 内部统计（O22） ----------------

    public Map<String, Object> statistics(Long userId) {
        Map<String, Object> st = orderMapper.statistics(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("soldCount", intOf(st == null ? null : st.get("soldCount")));
        data.put("boughtCount", intOf(st == null ? null : st.get("boughtCount")));
        data.put("pendingShipCount", intOf(st == null ? null : st.get("pendingShipCount")));
        data.put("pendingReceiveCount", intOf(st == null ? null : st.get("pendingReceiveCount")));
        Map<String, Object> rating = safeRating(userId);
        data.put("avgRating", rating.get("avgRating"));
        data.put("reviewCount", intOf(rating.get("reviewCount")));
        return data;
    }

    // ---------------- O16 后台列表 ----------------

    public PageResult<Map<String, Object>> adminPage(OrderQuery q) {
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<Order>()
                .eq(q.getStatus() != null && !q.getStatus().isBlank(), Order::getStatus, q.getStatus())
                .eq(q.getBuyerId() != null, Order::getBuyerId, q.getBuyerId())
                .eq(q.getSellerId() != null, Order::getSellerId, q.getSellerId())
                .orderByDesc(Order::getCreateTime);
        Page<Order> page = orderMapper.selectPage(new Page<>(Math.max(1, q.getPageNum()), Math.min(100, Math.max(1, q.getPageSize()))), w);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order o : page.getRecords()) {
            rows.add(card(o, null));
        }
        return PageResult.from(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Map<String, Object> adminStatistics() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", orderMapper.selectCount(null));
        for (String s : List.of("UNPAID", "UNSHIPPED", "UNRECEIVED", "COMPLETED", "CANCELLED", "REFUNDING", "REFUNDED")) {
            data.put(s, orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, s)));
        }
        return data;
    }

    // ---------------- 工具 ----------------

    public Order requireOrder(String orderNo) {
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (o == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return o;
    }

    private Map<String, Object> card(Order o, Long viewerId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderNo", o.getOrderNo());
        row.put("status", o.getStatus());
        row.put("productId", o.getProductId());
        row.put("totalAmount", o.getTotalAmount());
        row.put("freight", o.getFreight());
        row.put("payAmount", o.getPayAmount());
        row.put("buyerId", o.getBuyerId());
        row.put("sellerId", o.getSellerId());
        row.put("createTime", o.getCreateTime());
        if ("UNPAID".equals(o.getStatus())) {
            row.put("expireTime", o.getCreateTime().plusMinutes(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        OrderItem item = itemOf(o.getId());
        if (item != null) {
            row.put("productTitle", item.getProductTitle());
            row.put("productCover", item.getProductCover());
            row.put("price", item.getPrice());
        }
        Map<String, Object> peer = null;
        try {
            Long peerId = viewerId != null && viewerId.equals(o.getSellerId()) ? o.getBuyerId() : o.getSellerId();
            row.put("peerId", peerId);
            R<Map<String, Object>> r = userClient.brief(peerId);
            peer = r == null ? null : r.getData();
        } catch (Exception e) {
            log.debug("取交易对方信息失败：{}", e.getMessage());
        }
        if (peer != null) {
            row.put("counterpartNickname", peer.get("nickname"));
            row.put("counterpartAvatar", peer.get("avatar"));
        }
        // 卖家发货需要买家收货信息：来自下单时冻结的地址快照
        Map<String, Object> addr = addressOf(o);
        if (!addr.isEmpty()) {
            row.put("buyerName", addr.get("receiver"));
            row.put("buyerPhone", addr.get("phone"));
            String fullAddress = str(addr.get("province")) + str(addr.get("city"))
                    + str(addr.get("district")) + str(addr.get("detail"));
            row.put("buyerAddress", fullAddress);
            row.put("address", addr);
        }
        return row;
    }

    private List<Map<String, Object>> timeline(Long orderId) {
        List<OrderStatusLog> logs = logMapper.selectList(new LambdaQueryWrapper<OrderStatusLog>()
                .eq(OrderStatusLog::getOrderId, orderId).orderByAsc(OrderStatusLog::getCreateTime));
        List<Map<String, Object>> list = new ArrayList<>();
        for (OrderStatusLog l : logs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fromStatus", l.getFromStatus());
            row.put("toStatus", l.getToStatus());
            row.put("operatorType", l.getOperatorType());
            row.put("remark", l.getRemark());
            row.put("createTime", l.getCreateTime());
            list.add(row);
        }
        return list;
    }

    public void logStatus(Long orderId, String from, String to, Long operatorId, String operatorType, String remark) {
        OrderStatusLog l = new OrderStatusLog();
        l.setOrderId(orderId);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setOperatorId(operatorId);
        l.setOperatorType(operatorType);
        l.setRemark(remark);
        l.setCreateTime(LocalDateTime.now());
        logMapper.insert(l);
    }

    private OrderItem itemOf(Long orderId) {
        return itemMapper.selectOne(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId).last("LIMIT 1"));
    }

    public static String genOrderNo() {
        return "O" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + RandomUtil.randomNumbers(6);
    }

    // ---------------- 外部调用（失败降级） ----------------

    private Map<String, Object> viewProduct(Long productId) {
        R<Map<String, Object>> r = productClient.view(productId);
        if (r == null || r.getCode() != 0 || r.getData() == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return r.getData();
    }

    private Map<String, Object> address(Long userId, Long addressId) {
        R<Map<String, Object>> r = userClient.address(addressId, userId);
        if (r == null || r.getCode() != 0 || r.getData() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "收货地址不存在，请重新选择");
        }
        return r.getData();
    }

    private void safeRestore(Long productId, String orderNo) {
        try {
            productClient.restore(new ProductClient.StockBody(productId, orderNo));
        } catch (Exception e) {
            log.warn("回补商品失败，等补偿任务：productId={}, orderNo={}, err={}", productId, orderNo, e.getMessage());
        }
    }

    private void sendCard(String orderNo, String cardType, Long fromId, Long toId, Long productId,
                          String title, String statusText, String actionText) {
        try {
            Map<String, Object> product = productSnapshot(productId);
            String productTitle = title == null || title.isBlank() ? ProductClient.titleOf(product) : title;
            messageClient.card(new MessageClient.CardBody(orderNo, cardType, fromId, toId, productId,
                    productTitle, ProductClient.coverOf(product), statusText, actionText));
        } catch (Exception e) {
            log.warn("订单卡片推送失败（不影响主流程）：orderNo={}, cardType={}, err={}", orderNo, cardType, e.getMessage());
        }
    }

    /** 卡片需要商品名与封面；取不到时仍发卡片，但前端会退回会话商品快照。 */
    private Map<String, Object> productSnapshot(Long productId) {
        try {
            R<Map<String, Object>> r = productClient.view(productId);
            if (r != null && r.getCode() == 0 && r.getData() != null) {
                return r.getData();
            }
        } catch (Exception e) {
            log.warn("读取订单卡片商品快照失败：productId={}, err={}", productId, e.getMessage());
        }
        return Map.of();
    }

    private Map<String, Object> safeRating(Long userId) {
        try {
            Map<String, Object> r = reviewMapper.ratingOf(userId);
            return r == null ? Map.of() : r;
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> addressOf(Order o) {
        if (o.getAddressSnapshot() == null) {
            return Map.of();
        }
        try {
            return cn.hutool.json.JSONUtil.toBean(o.getAddressSnapshot(), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return cn.hutool.json.JSONUtil.toJsonStr(map);
        } catch (Exception e) {
            return null;
        }
    }

    static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    static Long longOf(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int intOf(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return o == null ? 0 : Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static BigDecimal priceOf(Map<String, Object> product) {
        Object p = product == null ? null : product.get("price");
        if (p == null) {
            throw new BizException(ErrorCode.PRODUCT_UNAVAILABLE, "商品价格异常");
        }
        return new BigDecimal(String.valueOf(p));
    }
}
