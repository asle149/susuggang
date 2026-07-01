package com.susuggang.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtTokenProvider {

    // 실서비스는 환경변수/설정으로 뺄 것
    private final SecretKey key = Keys.hmacShaKeyFor(
            "susuggang-secret-key-change-me-please-at-least-32bytes".getBytes());
    private final long validityMs = 1000L * 60 * 60;

    public String createToken(Long memberId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMs))
                .signWith(key)
                .compact();
    }

    public Long getMemberId(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.valueOf(subject);
    }
}
