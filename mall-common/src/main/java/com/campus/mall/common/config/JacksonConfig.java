package com.campus.mall.common.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 序列化约定（01 §6.5 / 04 §3）：
 * - Long / long 一律序列化成字符串，避免前端 JS 精度丢失（雪花 ID 有 19 位）
 * - BigDecimal 也序列化成字符串（"199.00"），前端 formatMoney 处理
 * - 时间统一 yyyy-MM-dd HH:mm:ss
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer mallJacksonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
            module.addSerializer(LocalDateTime.class,
                    new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            module.addSerializer(LocalDate.class,
                    new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            module.addSerializer(LocalTime.class,
                    new com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer(
                            DateTimeFormatter.ofPattern("HH:mm:ss")));
            module.addDeserializer(LocalDateTime.class,
                    new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            builder.modules(module);
            builder.featuresToDisable(
                    com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}
