package com.campus.mall.payment.task;

import com.campus.mall.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 清理 30 分钟前仍是 INIT 的支付单（02 §7.1：close_time 由此任务写入）。
 * 订单取消不调支付服务，两边靠各自的定时任务收敛。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCloseTask {

    private final PaymentService paymentService;

    @Scheduled(cron = "0 */10 * * * ?")
    public void closeStale() {
        int rows = paymentService.closeStale(30);
        if (rows > 0) {
            log.info("清理超时未支付单：{} 条", rows);
        }
    }
}
