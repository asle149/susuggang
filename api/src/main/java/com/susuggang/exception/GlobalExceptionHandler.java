package com.susuggang.exception;

import com.susuggang.dto.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CommonResponse<String> handleIllegalState(IllegalStateException e) {
        return CommonResponse.fail(HttpStatus.CONFLICT.value(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse<String> handleIllegalArgument(IllegalArgumentException e) {
        return CommonResponse.fail(HttpStatus.BAD_REQUEST.value(), e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public CommonResponse<String> handleNoSuchElement(NoSuchElementException e) {
        return CommonResponse.fail(HttpStatus.NOT_FOUND.value(), e.getMessage());
    }
}
