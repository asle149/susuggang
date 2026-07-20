package com.susuggang.service;

import com.susuggang.config.JwtTokenProvider;
import com.susuggang.domain.Member;
import com.susuggang.domain.Role;
import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import com.susuggang.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public Long signup(String email, String password) {
        Member member = memberRepository.save(Member.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(Role.USER)
                .build());
        return member.getId();
    }

    public String login(String email, String password) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return tokenProvider.createToken(member.getId());
    }
}
