package com.campus.mall.common.config;

import com.campus.mall.common.exception.GlobalExceptionHandler;
import com.campus.mall.common.mybatis.MybatisPlusConfig;
import com.campus.mall.common.security.RequireRoleAspect;
import com.campus.mall.common.security.UserContextFilter;
import com.campus.mall.common.web.InternalTokenFilter;
import com.campus.mall.common.web.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

/**
 * mall-common 自动装配：各服务只要引依赖就自动获得
 * traceId、当前用户上下文、/inner/** 内部凭据校验、@RequireRole、统一异常与序列化。
 * 注册文件见 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({JacksonConfig.class, GlobalExceptionHandler.class, FeignInternalConfiguration.class,
        com.campus.mall.common.storage.LocalFileStorage.class})
public class MallCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> bean = new FilterRegistrationBean<>(new TraceIdFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<UserContextFilter> userContextFilter() {
        FilterRegistrationBean<UserContextFilter> bean = new FilterRegistrationBean<>(new UserContextFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilter(
            ObjectMapper objectMapper,
            @Value("${mall.internal.token:}") String internalToken) {
        FilterRegistrationBean<InternalTokenFilter> bean = new FilterRegistrationBean<>(
                new InternalTokenFilter(internalToken, objectMapper));
        bean.addUrlPatterns("/inner/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean
    public RequireRoleAspect requireRoleAspect() {
        return new RequireRoleAspect();
    }

    /**
     * 只有引了 mybatis-plus 的服务才装配分页与审计字段填充。
     */
    @AutoConfiguration
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor")
    @Import(MybatisPlusConfig.class)
    public static class MallMybatisAutoConfiguration {
    }
}
