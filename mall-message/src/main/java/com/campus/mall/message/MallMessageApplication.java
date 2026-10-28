package com.campus.mall.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 消息服务（4405）：会话与聊天消息、订单状态卡片、系统通知、WebSocket 推送与强制下线。
 */
@EnableFeignClients
@EnableDiscoveryClient
@MapperScan("com.campus.mall.message.mapper")
@SpringBootApplication
public class MallMessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallMessageApplication.class, args);
    }
}
