package com.susuggang.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.susuggang.exception.ErrorCode;

import java.util.Map;

// 응답 봉투 — HTTP 상태와 별개로 body의 code(자체 코드 이넘)가 세부 사유를 나른다
public record CommonResponse<T>(
        String code,
        String msg,
        T value,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> errorInfo) {

    public static <T> CommonResponse<T> success(T value) {
        return new CommonResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), value, null);
    }

    public static CommonResponse<Void> ok() {
        return new CommonResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), null, null);
    }

    public static CommonResponse<Void> fail(ErrorCode errorCode) {
        return fail(errorCode, null);
    }

    public static CommonResponse<Void> fail(ErrorCode errorCode, Map<String, Object> errorInfo) {
        return new CommonResponse<>(errorCode.getCode(), errorCode.getMsg(), null, errorInfo);
    }
}
