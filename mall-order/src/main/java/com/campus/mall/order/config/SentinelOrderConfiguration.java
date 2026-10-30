package com.campus.mall.order.config;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/** 从 Nacos 动态加载订单域熔断规则。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentinelOrderConfiguration {

    private final Environment environment;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void initialize() {
        reloadRules();
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange() {
        reloadRules();
    }

    private void reloadRules() {
        String json = environment.getProperty("mall.sentinel.order-degrade-rules", "[]");
        try {
            List<DegradeRule> rules = objectMapper.readValue(json, new TypeReference<>() { });
            DegradeRuleManager.loadRules(rules);
            log.info("Sentinel 订单熔断规则已刷新：{} 条", rules.size());
        } catch (Exception e) {
            log.error("Sentinel 订单熔断规则解析失败，保留现有规则：{}", e.getMessage());
        }
    }
}
