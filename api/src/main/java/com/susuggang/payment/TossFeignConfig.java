package com.susuggang.payment;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// @Configuration 없이 @FeignClient(configuration=)으로만 연결 — 이 설정이 전역 빈으로 새지 않게
public class TossFeignConfig {

    @Bean
    public RequestInterceptor tossAuthInterceptor(@Value("${toss.secret-key}") String secretKey) {
        // 토스 규칙: "시크릿키:" (비밀번호 없음을 뜻하는 콜론 포함)를 base64 → Basic 인증 헤더
        String encoded = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return template -> template.header("Authorization", "Basic " + encoded);
    }
}
