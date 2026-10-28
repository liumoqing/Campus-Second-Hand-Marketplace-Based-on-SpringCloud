package com.campus.mall.common.config;

import com.campus.mall.common.constant.MallConstants;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 调用统一带上内部令牌与 traceId（03 §1.8）。
 * 只有引入了 feign-core 的服务才会装配这个 Bean，网关/纯 Web 服务不受影响。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RequestInterceptor.class)
public class FeignInternalConfiguration {

    @Value("${mall.internal.token:mall-internal-2026}")
    private String internalToken;

    @Bean
    public RequestInterceptor internalTokenInterceptor() {
        return template -> {
            template.header(MallConstants.HEADER_INTERNAL_TOKEN, internalToken);
            String traceId = MDC.get(MallConstants.MDC_TRACE_ID);
            if (traceId != null && !traceId.isBlank()) {
                template.header(MallConstants.HEADER_TRACE_ID, traceId);
            }
        };
    }
}
