package com.campus.mall.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Nacos 驱动的 Sentinel 网关规则与统一 429 响应。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentinelGatewayConfiguration {

    private final Environment environment;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void initialize() {
        GatewayCallbackManager.setBlockHandler(this::blocked);
        loadApiDefinitions();
        reloadRules();
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange() {
        reloadRules();
    }

    private void reloadRules() {
        String json = environment.getProperty("mall.sentinel.gateway-flow-rules", "[]");
        try {
            Set<GatewayFlowRule> rules = objectMapper.readValue(json, new TypeReference<>() { });
            GatewayRuleManager.loadRules(rules);
            log.info("Sentinel 网关规则已刷新：{} 条", rules.size());
        } catch (Exception e) {
            log.error("Sentinel 网关规则解析失败，保留现有规则：{}", e.getMessage());
        }
    }

    private void loadApiDefinitions() {
        ApiPathPredicateItem productPage = new ApiPathPredicateItem()
                .setPattern("/product/page")
                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT);
        ApiDefinition definition = new ApiDefinition("mall-product-page")
                .setPredicateItems(Set.of(productPage));
        GatewayApiDefinitionManager.loadApiDefinitions(Set.of(definition));
    }

    private Mono<ServerResponse> blocked(ServerWebExchange exchange, Throwable error) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", exchange.getRequest().getPath().value());
        data.put("reason", "FLOW_LIMIT");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 10007);
        body.put("message", "请求过于频繁，请稍后再试");
        body.put("data", data);
        body.put("timestamp", System.currentTimeMillis());

        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }
}
