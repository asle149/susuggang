package com.susuggang.dto;

public record CommonResponse<T>(Integer status, String msg, T value) {

    public static <T> CommonResponse<T> success(T value){
        return new CommonResponse<>(
                200, "성공", value
        );
    }

    public static <T> CommonResponse<T> fail(Integer status, String msg) {
        return new CommonResponse<>(status, msg, null);
    }

    public static CommonResponse<Void> ok() {
        return new CommonResponse<>(200, "성공", null);
    }
}
