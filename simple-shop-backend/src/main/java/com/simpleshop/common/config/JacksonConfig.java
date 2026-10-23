package com.simpleshop.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 序列化约定。
 *
 * <p>请求体出现契约未定义的字段时直接报错，避免拼写错误被静默忽略；
 * 时间始终按 ISO-8601 字符串输出，不输出纪元时间戳。
 * 不配置 NON_NULL，保证空字段的键仍然存在。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer simpleShopJacksonCustomizer() {
        return builder -> builder
                .failOnUnknownProperties(true)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
