package com.simpleshop.common.config;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.simpleshop.common.auth.AuthInterceptor;
import com.simpleshop.common.auth.AuthRole;
import com.simpleshop.common.auth.SessionStore;

/**
 * Web 层配置：注册买卖双方的鉴权拦截器、映射图片目录、启用定时任务。
 *
 * <p>登录与注册端点从各自角色的拦截器中排除；图片资源公开访问。
 */
@Configuration
@EnableScheduling
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    public static final String SELLER_API_PREFIX = "/api/seller/**";
    public static final String SELLER_SESSION_PATH = "/api/seller/auth/session";

    public static final String BUYER_API_PREFIX = "/api/buyer/**";
    public static final String BUYER_REGISTER_PATH = "/api/buyer/auth/register";
    public static final String BUYER_SESSION_PATH = "/api/buyer/auth/session";

    private final SessionStore sessionStore;
    private final AppProperties properties;

    public WebMvcConfig(SessionStore sessionStore, AppProperties properties) {
        this.sessionStore = sessionStore;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(AuthRole.SELLER, sessionStore))
                .addPathPatterns(SELLER_API_PREFIX)
                .excludePathPatterns(SELLER_SESSION_PATH)
                .order(10);

        registry.addInterceptor(new AuthInterceptor(AuthRole.BUYER, sessionStore))
                .addPathPatterns(BUYER_API_PREFIX)
                .excludePathPatterns(BUYER_REGISTER_PATH, BUYER_SESSION_PATH)
                .order(20);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        AppProperties.Image image = properties.getImage();
        String location = image.directoryPath().toString();
        if (!location.endsWith(File.separator)) {
            location = location + File.separator;
        }
        registry.addResourceHandler(image.getUrlPrefix() + "/**")
                .addResourceLocations("file:" + location);

        if (!image.directoryPath().toFile().isDirectory()) {
            log.warn("image directory does not exist yet: {} (url prefix: {})",
                    image.directoryPath(), image.getUrlPrefix());
        } else {
            log.info("serving images: {} -> {}", image.getUrlPrefix() + "/**", location);
        }
    }
}
