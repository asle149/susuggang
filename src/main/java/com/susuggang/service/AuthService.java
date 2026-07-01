package com.susuggang.service;

import com.susuggang.config.JwtTokenProvider;
import com.susuggang.domain.Member;
import com.susuggang.domain.Role;
import com.susuggang.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

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
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 틀렸습니다"));
        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 틀렸습니다");
        }
        return tokenProvider.createToken(member.getId());
    }
}
