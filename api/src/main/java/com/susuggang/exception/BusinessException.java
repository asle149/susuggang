package com.susuggang.exception;

import lombok.Getter;

import java.util.Map;

// 서비스가 던지는 업무 예외 — 어떤 코드·HTTP 상태로 응답할지는 ErrorCode가 들고 간다
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> errorInfo;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, Map<String, Object> errorInfo) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
        this.errorInfo = errorInfo;
    }
}
