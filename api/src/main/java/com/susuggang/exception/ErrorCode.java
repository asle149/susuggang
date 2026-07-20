package com.susuggang.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// body에 실리는 자체 응답 코드 — SSG + 5자리. 대역: 0=성공 / 1만=공통·인증 / 2만=주문·재고 / 3만=결제(예약) / 9만=정책
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    SUCCESS("SSG00000", HttpStatus.OK, "성공"),

    // 공통·인증 (SSG1xxxx)
    INVALID_REQUEST("SSG10001", HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),
    LOGIN_FAILED("SSG10002", HttpStatus.BAD_REQUEST, "이메일 또는 비밀번호가 틀렸습니다"),
    RESOURCE_NOT_FOUND("SSG10003", HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다"),
    CONFLICT_STATE("SSG10004", HttpStatus.CONFLICT, "요청을 처리할 수 없는 상태입니다"),

    // 주문·재고 (SSG2xxxx)
    PRODUCT_NOT_FOUND("SSG20001", HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다"),
    OUT_OF_STOCK("SSG20002", HttpStatus.CONFLICT, "재고 부족"),
    ORDER_NOT_FOUND("SSG20003", HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다"),
    ORDER_NOT_CONFIRMABLE("SSG20004", HttpStatus.CONFLICT, "확정할 수 없는 주문"),

    // 결제 (SSG3xxxx)
    PAYMENT_AMOUNT_MISMATCH("SSG30001", HttpStatus.BAD_REQUEST, "결제 금액이 주문 금액과 다릅니다"),
    PAYMENT_CONFIRM_FAILED("SSG30002", HttpStatus.BAD_GATEWAY, "결제 승인에 실패했습니다"),

    // 정책 (SSG9xxxx)
    FORCE_UPDATE("SSG99999", HttpStatus.OK, "앱 업데이트가 필요합니다");   // 클라이언트와 "이 코드 = 강제 업데이트 화면" 사전 약속용 예약

    private final String code;
    private final HttpStatus status;
    private final String msg;
}
