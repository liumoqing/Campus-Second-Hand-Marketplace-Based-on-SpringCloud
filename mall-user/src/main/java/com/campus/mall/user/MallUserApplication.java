package com.campus.mall.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 用户服务（4401）：凭证、资料、地址、信用分、后台用户治理、卖家主页。
 */
@EnableFeignClients
@EnableDiscoveryClient
@EnableScheduling
@MapperScan("com.campus.mall.user.mapper")
@SpringBootApplication
public class MallUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallUserApplication.class, args);
    }
}
