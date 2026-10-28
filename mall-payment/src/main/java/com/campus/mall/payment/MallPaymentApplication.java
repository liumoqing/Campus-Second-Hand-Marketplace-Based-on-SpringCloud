package com.campus.mall.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 支付服务（4404）。
 */
@EnableFeignClients
@EnableDiscoveryClient
@EnableScheduling
@org.mybatis.spring.annotation.MapperScan("com.campus.mall.payment.mapper")
@SpringBootApplication
public class MallPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallPaymentApplication.class, args);
    }
}
