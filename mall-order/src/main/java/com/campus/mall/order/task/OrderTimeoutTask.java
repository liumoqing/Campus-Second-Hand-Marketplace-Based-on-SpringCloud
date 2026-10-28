package com.campus.mall.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.mall.order.client.ProductClient;
import com.campus.mall.order.entity.Order;
import com.campus.mall.order.mapper.OrderMapper;
import com.campus.mall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时未支付自动取消（01 §8.6）。
 *
 * <p>正式环境用 RocketMQ 延迟消息 {@code mall-order-timeout}；本机没有 RocketMQ，用这个定时任务兜底，
 * 两者语义等价：取消订单 → 商品从 LOCKED 回补为 ON_SALE。每 5 分钟扫一次，取消 30 分钟前的待付款单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private static final int TIMEOUT_MINUTES = 30;

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final ProductClient productClient;

    @Scheduled(cron = "0 */5 * * * ?")
    public void cancelTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<Order> list = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "UNPAID")
                .lt(Order::getCreateTime, deadline)
                .last("LIMIT 100"));
        if (list.isEmpty()) {
            return;
        }
        for (Order o : list) {
            try {
                int rows = orderMapper.cancelUnpaid(o.getOrderNo(), LocalDateTime.now(), "支付超时，系统自动取消");
                if (rows == 0) {
                    continue;
                }
                orderService.logStatus(o.getId(), "UNPAID", "CANCELLED", null, "SYSTEM", "支付超时自动关闭");
                productClient.restore(new ProductClient.StockBody(o.getProductId(), o.getOrderNo()));
                log.info("超时订单已取消：{}", o.getOrderNo());
            } catch (Exception e) {
                log.warn("超时订单处理失败：orderNo={}, err={}", o.getOrderNo(), e.getMessage());
            }
        }
    }
}
