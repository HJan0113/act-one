package com.hjan.actone.user.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfiguration implements WebMvcConfigurer {

    private final UserContextInterceptor userContextInterceptor;

    public SaTokenConfiguration(UserContextInterceptor userContextInterceptor) {
        this.userContextInterceptor = userContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
                    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attributes != null && shouldSkip(attributes.getRequest())) {
                        return;
                    }
                    StpUtil.checkLogin();
                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/auth/login", "/error");

        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/auth/login", "/error");
    }

    private boolean shouldSkip(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ASYNC
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }
}
