package com.susuggang.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class LoggingConfig {

    @Bean
    public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilter() {
        FilterRegistrationBean<MdcLoggingFilter> registration = new FilterRegistrationBean<>(new MdcLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);   // 시큐리티 체인(-100)보다 앞 — 401/403 응답 로그에도 traceId가 남게
        return registration;
    }
}
