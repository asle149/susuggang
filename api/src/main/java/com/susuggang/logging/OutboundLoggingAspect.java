package com.susuggang.logging;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

// 아웃바운드 공통 로깅 — MDC 필터가 인바운드 길목이라면 이건 나가는 호출의 길목.
// traceId·memberId는 로그백 패턴이 MDC에서 자동으로 붙인다
@Slf4j
@Aspect
@Component
public class OutboundLoggingAspect {

    private static final int BODY_MAX = 300;

    @Around("execution(* com.susuggang.payment.TossPaymentClient.*(..))")
    public Object logOutbound(ProceedingJoinPoint joinPoint) throws Throwable {
        String call = "toss." + joinPoint.getSignature().getName();
        String args = Arrays.toString(joinPoint.getArgs());
        long start = System.currentTimeMillis();
        log.info("[outbound] {} 요청: {}", call, args);
        try {
            Object result = joinPoint.proceed();
            log.info("[outbound] {} 성공 ({}ms): {}", call, elapsed(start), truncate(String.valueOf(result)));
            return result;
        } catch (FeignException e) {
            log.warn("[outbound] {} 실패 ({}ms): httpStatus={}, body={}",
                    call, elapsed(start), e.status(), truncate(e.contentUTF8()));
            throw e;
        } catch (Throwable t) {
            log.warn("[outbound] {} 예외 ({}ms): {}", call, elapsed(start), t.getClass().getSimpleName());
            throw t;
        }
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > BODY_MAX ? body.substring(0, BODY_MAX) + "…" : body;
    }
}
