package com.campus.mall.payment.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.payment.client.OrderClient;
import com.campus.mall.payment.dto.MockCallbackDTO;
import com.campus.mall.payment.dto.RefundDTO;
import com.campus.mall.payment.entity.Payment;
import com.campus.mall.payment.mapper.PaymentMapper;
import com.campus.mall.payment.vo.PaymentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付（03 §6）：一步模拟支付，没有钱包、没有真实资金。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentMapper paymentMapper;
    private final OrderClient orderClient;

    @Value("${mall.payment.mock.secret:qingchuan-mock-secret-2026}")
    private String mockSecret;

    // ---------------- Y1 支付 ----------------

    @Transactional(rollbackFor = Exception.class)
    public PaymentVO pay(Long userId, String orderNo) {
        Map<String, Object> order = fetchOrder(orderNo);
        Long buyerId = longOf(order.get("buyerId"));
        if (buyerId != null && !buyerId.equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能支付自己的订单");
        }
        String orderStatus = str(order.get("status"));
        Payment exist = findByOrderNo(orderNo);
        if (exist != null && "SUCCESS".equals(exist.getStatus())) {
            // 幂等：订单已支付时原样返回，不报错（前端按 SUCCESS 处理）
            return toVO(exist);
        }
        if (!"UNPAID".equals(orderStatus)) {
            throw new BizException(ErrorCode.PAY_STATUS_NOT_ALLOWED);
        }

        BigDecimal amount = OrderClient.amountOf(order);
        LocalDateTime now = LocalDateTime.now();
        Payment p = exist != null ? exist : new Payment();
        if (exist == null) {
            p.setPayNo("P" + now.format(TS) + RandomUtil.randomNumbers(6));
            p.setOrderNo(orderNo);
            p.setUserId(userId);
            p.setAmount(amount);
            p.setChannel("MOCK");
            p.setStatus("INIT");
            p.setRefundAmount(BigDecimal.ZERO);
            // t_payment 的 create_time/update_time 是 NOT NULL 且没有 DEFAULT（02 DDL），必须自己写
            p.setCreateTime(now);
            p.setUpdateTime(now);
            paymentMapper.insert(p);
        }
        // 模拟网关：立即回写成功
        p.setStatus("SUCCESS");
        p.setTradeNo("MOCK-" + p.getPayNo().substring(Math.max(0, p.getPayNo().length() - 6)));
        p.setPayTime(now);
        p.setUpdateTime(now);
        paymentMapper.updateById(p);

        notifyOrderPaid(orderNo, p);
        return toVO(p);
    }

    // ---------------- Y2 详情 ----------------

    public PaymentVO detail(String payNo, Long userId, boolean admin) {
        Payment p = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getPayNo, payNo));
        if (p == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        if (!admin && !userId.equals(p.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return toVO(p);
    }

    // ---------------- Y3 模拟回调 ----------------

    @Transactional(rollbackFor = Exception.class)
    public PaymentVO callback(MockCallbackDTO dto) {
        String expect = DigestUtil.md5Hex(dto.getPayNo() + dto.getTradeNo() + dto.getStatus() + mockSecret);
        if (!expect.equalsIgnoreCase(dto.getSign())) {
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "验签失败");
        }
        Payment p = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getPayNo, dto.getPayNo()));
        if (p == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        if ("SUCCESS".equals(p.getStatus()) || "REFUNDED".equals(p.getStatus())) {
            return toVO(p);
        }
        p.setStatus("SUCCESS".equals(dto.getStatus()) ? "SUCCESS" : "FAILED");
        p.setTradeNo(dto.getTradeNo());
        p.setPayTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        paymentMapper.updateById(p);
        if ("SUCCESS".equals(p.getStatus())) {
            notifyOrderPaid(p.getOrderNo(), p);
        }
        return toVO(p);
    }

    // ---------------- Y4 退款 ----------------

    @Transactional(rollbackFor = Exception.class)
    public boolean refund(RefundDTO dto) {
        Payment p = findByOrderNo(dto.getOrderNo());
        if (p == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        // 已退款直接幂等成功；第二次进入时 refundable 已经是 0，不能先做额度校验
        if ("REFUNDED".equals(p.getStatus())) {
            return true;
        }
        BigDecimal refundable = p.getAmount().subtract(
                p.getRefundAmount() == null ? BigDecimal.ZERO : p.getRefundAmount());
        if (dto.getAmount().compareTo(refundable) > 0) {
            throw new BizException(ErrorCode.REFUND_AMOUNT_EXCEEDED);
        }
        int rows = paymentMapper.markRefunded(dto.getOrderNo(), dto.getAmount());
        if (rows == 0) {
            // 影响 0 行：已退过或不是 SUCCESS，按幂等处理
            log.info("退款幂等命中：orderNo={} status={}", dto.getOrderNo(), p.getStatus());
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("refundAmount", dto.getAmount());
            body.put("reason", dto.getReason());
            orderClient.notifyRefunded(dto.getOrderNo(), body);
        } catch (Exception e) {
            log.error("退款成功但通知订单服务失败，需对账：orderNo={}", dto.getOrderNo(), e);
        }
        return true;
    }

    // ---------------- Y5 字典 ----------------

    public Map<String, List<Map<String, Object>>> dict() {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        m.put("channel", List.of(dictItem("MOCK", "模拟支付")));
        m.put("status", List.of(
                dictItem("INIT", "待支付"),
                dictItem("SUCCESS", "支付成功"),
                dictItem("REFUNDED", "已退款"),
                dictItem("CLOSED", "已关闭"),
                dictItem("FAILED", "支付失败")));
        return m;
    }

    // ---------------- Y6 后台分页 ----------------

    public PageResult<PaymentVO> adminPage(int pageNum, int pageSize, String status, String channel, String keyword) {
        LambdaQueryWrapper<Payment> q = new LambdaQueryWrapper<Payment>()
                .eq(StringUtils.hasText(status), Payment::getStatus, status)
                .eq(StringUtils.hasText(channel), Payment::getChannel, channel)
                .orderByDesc(Payment::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(Payment::getPayNo, keyword).or().like(Payment::getOrderNo, keyword));
        }
        Page<Payment> page = paymentMapper.selectPage(new Page<>(pageNum, Math.min(pageSize, 100)), q);
        return PageResult.from(page.convert(this::toVO));
    }

    // ---------------- 内部接口 ----------------

    public PaymentVO detailByOrderNo(String orderNo) {
        Payment p = findByOrderNo(orderNo);
        if (p == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        return toVO(p);
    }

    /** 定时任务用：把 30 分钟前仍是 INIT 的单置 CLOSED（02 §7.1） */
    public int closeStale(int minutes) {
        return paymentMapper.closeStale(minutes);
    }

    // ---------------- 内部方法 ----------------

    private Payment findByOrderNo(String orderNo) {
        return paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
    }

    private Map<String, Object> fetchOrder(String orderNo) {
        R<Map<String, Object>> r;
        try {
            r = orderClient.getByOrderNo(orderNo);
        } catch (Exception e) {
            log.error("调用 mall-order 取订单失败：orderNo={}", orderNo, e);
            throw new BizException(ErrorCode.SERVICE_DEGRADED, "订单服务暂不可用");
        }
        if (r == null || r.getData() == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return r.getData();
    }

    private void notifyOrderPaid(String orderNo, Payment p) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("payNo", p.getPayNo());
            body.put("payAmount", p.getAmount());
            body.put("payTime", p.getPayTime());
            orderClient.notifyPaid(orderNo, body);
        } catch (Exception e) {
            // 本机没有 RocketMQ，这里是 docs 里"MQ 失败兜底"的唯一通道：记日志 + 可重试
            log.error("支付成功但通知订单服务失败，需对账：orderNo={}, payNo={}", orderNo, p.getPayNo(), e);
        }
    }

    private PaymentVO toVO(Payment p) {
        PaymentVO vo = new PaymentVO();
        vo.setPayNo(p.getPayNo());
        vo.setOrderNo(p.getOrderNo());
        vo.setUserId(p.getUserId());
        vo.setAmount(p.getAmount());
        vo.setPayAmount(p.getAmount());
        vo.setChannel(p.getChannel());
        vo.setStatus(p.getStatus());
        vo.setTradeNo(p.getTradeNo());
        vo.setPayTime(p.getPayTime());
        vo.setRefundAmount(p.getRefundAmount());
        vo.setRefundTime(p.getRefundTime());
        vo.setCreateTime(p.getCreateTime());
        return vo;
    }

    private static Map<String, Object> dictItem(String value, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("label", label);
        return m;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long longOf(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
