package com.susuggang.exception;

import com.susuggang.dto.CommonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(CommonResponse.fail(errorCode, e.getErrorInfo()));
    }

    // 아래 셋은 아직 BusinessException으로 안 바뀐 코드의 안전망 — 원문 메시지("No value present" 등)를 노출하지 않고 공통 코드로 내린다
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("미분류 IllegalArgumentException: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(CommonResponse.fail(ErrorCode.INVALID_REQUEST));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<CommonResponse<Void>> handleIllegalState(IllegalStateException e) {
        log.warn("미분류 IllegalStateException: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.CONFLICT_STATE.getStatus())
                .body(CommonResponse.fail(ErrorCode.CONFLICT_STATE));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<CommonResponse<Void>> handleNoSuchElement(NoSuchElementException e) {
        log.warn("미분류 NoSuchElementException: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(CommonResponse.fail(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
