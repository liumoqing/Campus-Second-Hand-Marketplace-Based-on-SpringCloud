package com.campus.mall.order.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.order.client.MessageClient;
import com.campus.mall.order.client.PaymentClient;
import com.campus.mall.order.client.ProductClient;
import com.campus.mall.order.dto.RefundDtos;
import com.campus.mall.order.entity.Order;
import com.campus.mall.order.entity.Refund;
import com.campus.mall.order.mapper.OrderMapper;
import com.campus.mall.order.mapper.RefundMapper;
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
 * O12/O13 退款与售后（01 §8.9、02 §6.5）。
 *
 * <p>三个可申请窗口：未发货（随时）、已发货待收货（收货前）、已完成（收货后 7 天内，超期 40008）。
 * 一个订单只能申请一次（{@code t_refund.uk_order}）；拒绝后按 {@code before_status} 回退，不写死状态。
 * 退款**不走 Seata 全局事务**：payment 本地改单 → 返回成功后 order 改状态 + 回补商品。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final List<String> APPLICABLE = List.of("UNSHIPPED", "UNRECEIVED", "COMPLETED");
    private static final int AFTER_SALE_DAYS = 7;

    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final PaymentClient paymentClient;
    private final ProductClient productClient;
    private final MessageClient messageClient;

    // ---------------- O12 申请退款/售后 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> apply(Long buyerId, RefundDtos.Apply dto) {
        Order order = orderService.requireOrder(dto.getOrderNo());
        if (!Objects.equals(order.getBuyerId(), buyerId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        // 一单只能退一次：先判重（此时订单可能已是 REFUNDING，也要给 40007 而不是 40002）
        Long exists = refundMapper.selectCount(new LambdaQueryWrapper<Refund>().eq(Refund::getOrderId, order.getId()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.REFUND_DUPLICATED);
        }
        if (!APPLICABLE.contains(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "当前订单状态不能申请退款");
        }
        // 收货后 7 天内才可售后
        if ("COMPLETED".equals(order.getStatus())) {
            LocalDateTime finish = order.getFinishTime() == null ? order.getReceiveTime() : order.getFinishTime();
            if (finish != null && finish.plusDays(AFTER_SALE_DAYS).isBefore(LocalDateTime.now())) {
                throw new BizException(ErrorCode.REFUND_EXPIRED);
            }
        }
        BigDecimal amount = dto.getAmount() == null ? order.getPayAmount() : dto.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(order.getPayAmount()) > 0) {
            throw new BizException(ErrorCode.REFUND_AMOUNT_EXCEEDED);
        }
        String beforeStatus = order.getStatus();
        if (orderMapper.toRefunding(order.getOrderNo(), beforeStatus, LocalDateTime.now()) == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "订单状态已变化，请刷新重试");
        }

        LocalDateTime now = LocalDateTime.now();
        Refund refund = new Refund();
        refund.setRefundNo("R" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + RandomUtil.randomNumbers(6));
        refund.setOrderId(order.getId());
        refund.setBuyerId(buyerId);
        refund.setSellerId(order.getSellerId());
        refund.setAmount(amount);
        refund.setReason(dto.getReason());
        refund.setStatus("APPLYING");
        refund.setBeforeStatus(beforeStatus);
        refund.setApplyTime(now);
        // t_refund 的 create_time/update_time 是 NOT NULL 且无默认值，且 Refund 未继承 BaseEntity，必须手动填
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        refundMapper.insert(refund);

        orderService.logStatus(order.getId(), beforeStatus, "REFUNDING", buyerId, "BUYER", "申请退款：" + dto.getReason());
        notifySellerRefundApplied(order, amount);
        return Map.of("refundId", refund.getId(), "refundNo", refund.getRefundNo(),
                "amount", amount, "status", "APPLYING");
    }

    /** 买家申请退款后通知卖家处理；通知失败不影响退款单落库。 */
    private void notifySellerRefundApplied(Order order, BigDecimal amount) {
        String content = "买家对订单 " + order.getOrderNo() + " 申请退款，金额 ¥" + amount
                + "，请进入订单页「退款/售后」处理。";
        try {
            messageClient.notice(new MessageClient.NoticeBody(
                    order.getSellerId(), "REFUND", "退款申请待处理", content,
                    order.getOrderNo(), "ORDER"));
        } catch (Exception e) {
            log.warn("退款申请通知卖家失败：orderNo={}, err={}", order.getOrderNo(), e.getMessage());
        }
        try {
            Map<String, Object> product = productSnapshot(order.getProductId());
            messageClient.card(new MessageClient.CardBody(order.getOrderNo(), "REFUND_APPLIED",
                    order.getBuyerId(), order.getSellerId(), order.getProductId(),
                    ProductClient.titleOf(product), ProductClient.coverOf(product),
                    "买家申请退款，请处理", "处理退款"));
        } catch (Exception e) {
            log.warn("退款申请订单卡片推送失败：orderNo={}, err={}", order.getOrderNo(), e.getMessage());
        }
    }

    /** 卖家处理退款后通知买家，让审批结果形成完整闭环。 */
    private void notifyBuyerRefundHandled(Order order, Refund refund, boolean agreed, String remark) {
        String title = agreed ? "退款已同意" : "退款申请已拒绝";
        String content = agreed
                ? "卖家已同意订单 " + order.getOrderNo() + " 的退款申请，退款金额 ¥" + refund.getAmount()
                    + "，请留意退款到账。"
                : "卖家拒绝了订单 " + order.getOrderNo() + " 的退款申请。"
                    + (remark == null || remark.isBlank() ? "" : "原因：" + remark);
        try {
            messageClient.notice(new MessageClient.NoticeBody(
                    order.getBuyerId(), "REFUND", title, content,
                    order.getOrderNo(), "ORDER"));
        } catch (Exception e) {
            log.warn("退款处理结果通知买家失败：orderNo={}, err={}", order.getOrderNo(), e.getMessage());
        }
        if (agreed) {
            try {
                Map<String, Object> product = productSnapshot(order.getProductId());
                messageClient.card(new MessageClient.CardBody(order.getOrderNo(), "REFUND_DONE",
                        order.getSellerId(), order.getBuyerId(), order.getProductId(),
                        ProductClient.titleOf(product), ProductClient.coverOf(product),
                        "退款已完成，金额将原路退回", "查看订单"));
            } catch (Exception e) {
                log.warn("退款完成订单卡片推送失败：orderNo={}, err={}", order.getOrderNo(), e.getMessage());
            }
        }
    }

    /** 卡片展示商品快照；商品服务异常时通知照常发送，前端用会话商品信息兜底。 */
    private Map<String, Object> productSnapshot(Long productId) {
        try {
            var r = productClient.view(productId);
            if (r != null && r.getCode() == 0 && r.getData() != null) {
                return r.getData();
            }
        } catch (Exception e) {
            log.warn("读取退款卡片商品快照失败：productId={}, err={}", productId, e.getMessage());
        }
        return Map.of();
    }

    // ---------------- O13 卖家处理 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handle(Long operatorId, Long refundId, RefundDtos.Handle dto) {
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "退款单不存在");
        }
        boolean admin = UserContext.isAdmin();
        if (!admin && !Objects.equals(refund.getSellerId(), operatorId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!"APPLYING".equals(refund.getStatus())) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "该退款单已处理");
        }
        Order order = orderService.requireOrder(orderNoOf(refund));
        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(dto.getAgree())) {
            // 先让支付服务退款（本地事务，成功才继续），失败就把异常抛回去，单据仍是 APPLYING
            paymentClient.refund(new PaymentClient.RefundBody(order.getOrderNo(), refund.getAmount(), refund.getReason()));
            refundMapper.transit(refundId, "REFUNDED", now, operatorId, dto.getRemark());
            orderMapper.refunded(order.getOrderNo(), now);
            // 未发货退款要把商品放回去继续卖；已发货/已收货不回收（01 §8.9）
            if ("UNSHIPPED".equals(refund.getBeforeStatus())) {
                try {
                    productClient.restore(new ProductClient.StockBody(order.getProductId(), order.getOrderNo()));
                } catch (Exception e) {
                    log.warn("退款后回补商品失败，等补偿任务：orderNo={}, err={}", order.getOrderNo(), e.getMessage());
                }
            }
            orderService.logStatus(order.getId(), "REFUNDING", "REFUNDED", operatorId,
                    admin ? "ADMIN" : "SELLER", "同意退款：" + (dto.getRemark() == null ? "" : dto.getRemark()));
            notifyBuyerRefundHandled(order, refund, true, dto.getRemark());
            return Map.of("refundId", refundId, "status", "REFUNDED", "orderStatus", "REFUNDED");
        }

        // 拒绝：订单按申请前的状态回退
        refundMapper.transit(refundId, "REJECTED", now, operatorId, dto.getRemark());
        orderMapper.rejectRefund(order.getOrderNo(), refund.getBeforeStatus(), now);
        orderService.logStatus(order.getId(), "REFUNDING", refund.getBeforeStatus(), operatorId,
                admin ? "ADMIN" : "SELLER", "拒绝退款：" + (dto.getRemark() == null ? "" : dto.getRemark()));
        notifyBuyerRefundHandled(order, refund, false, dto.getRemark());
        return Map.of("refundId", refundId, "status", "REJECTED", "orderStatus", refund.getBeforeStatus());
    }

    // ---------------- O14 退款单列表 ----------------

    public PageResult<Map<String, Object>> page(Long userId, String role, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<Refund> w = new LambdaQueryWrapper<>();
        if ("sold".equals(role)) {
            w.eq(Refund::getSellerId, userId);
        } else {
            w.eq(Refund::getBuyerId, userId);
        }
        if (status != null && !status.isBlank()) {
            w.eq(Refund::getStatus, status);
        }
        w.orderByDesc(Refund::getCreateTime);
        return doPage(w, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> adminPage(String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<Refund> w = new LambdaQueryWrapper<Refund>()
                .eq(status != null && !status.isBlank(), Refund::getStatus, status)
                .orderByDesc(Refund::getCreateTime);
        return doPage(w, pageNum, pageSize);
    }

    public Map<String, Object> detail(Long userId, Long refundId) {
        Refund r = refundMapper.selectById(refundId);
        if (r == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "退款单不存在");
        }
        if (!UserContext.isAdmin() && !Objects.equals(r.getBuyerId(), userId) && !Objects.equals(r.getSellerId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return toRow(r);
    }

    private PageResult<Map<String, Object>> doPage(LambdaQueryWrapper<Refund> w, int pageNum, int pageSize) {
        Page<Refund> page = refundMapper.selectPage(new Page<>(Math.max(1, pageNum), Math.min(50, Math.max(1, pageSize))), w);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Refund r : page.getRecords()) {
            rows.add(toRow(r));
        }
        return PageResult.from(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private Map<String, Object> toRow(Refund r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("refundId", r.getId());
        row.put("refundNo", r.getRefundNo());
        row.put("orderNo", orderNoOf(r));
        row.put("amount", r.getAmount());
        row.put("reason", r.getReason());
        row.put("status", r.getStatus());
        row.put("beforeStatus", r.getBeforeStatus());
        row.put("applyTime", r.getApplyTime());
        row.put("handleTime", r.getHandleTime());
        row.put("handleRemark", r.getHandleRemark());
        return row;
    }

    private String orderNoOf(Refund refund) {
        Order order = orderMapper.selectById(refund.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order.getOrderNo();
    }
}
