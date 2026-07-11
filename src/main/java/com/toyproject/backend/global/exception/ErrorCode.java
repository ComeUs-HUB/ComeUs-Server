package com.toyproject.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /*
    * 도메인 별로 패키징 필요
    */

    // Common
    INVALID_INPUT_VALUE(400, "C001", "유효하지 않은 입력값입니다."),
    RESOURCE_NOT_FOUND(404, "C002", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "C003", "서버 내부 오류가 발생했습니다."),

    // Auth
    DUPLICATE_EMAIL(409, "U001", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(401, "U002", "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(401, "U003", "인증이 필요합니다."),
    FORBIDDEN(403, "U004", "접근 권한이 없습니다."),
    INVALID_TOKEN(401, "U005", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(401, "U006", "만료된 토큰입니다."),

    // Product
    PRODUCT_NOT_FOUND(404, "P001", "상품을 찾을 수 없습니다."),
    OUT_OF_STOCK(409, "P002", "재고가 부족합니다."),

    // Category
    CATEGORY_NOT_FOUND(404, "CA001", "카테고리를 찾을 수 없습니다."),
    CATEGORY_HAS_CHILDREN(409, "CA002", "하위 카테고리가 존재하여 삭제할 수 없습니다."),

    // Cart
    CART_ITEM_NOT_FOUND(404, "CT001", "장바구니 항목을 찾을 수 없습니다."),
    CART_QUANTITY_EXCEEDED(409, "CT002", "재고를 초과한 수량입니다."),

    // Order
    ORDER_NOT_FOUND(404, "O001", "주문을 찾을 수 없습니다."),
    ORDER_CANCEL_NOT_ALLOWED(409, "O002", "배송 중인 주문은 취소할 수 없습니다."),

    // Payment
    PAYMENT_NOT_FOUND(404, "PAY001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_ALREADY_EXISTS(409, "PAY002", "이미 결제가 완료된 주문입니다."),

    // Review
    REVIEW_NOT_FOUND(404, "R001", "리뷰를 찾을 수 없습니다."),
    REVIEW_ALREADY_EXISTS(409, "R002", "이미 리뷰를 작성한 상품입니다."),
    REVIEW_NOT_ALLOWED(403, "R003", "배송 완료된 주문에 한해 리뷰를 작성할 수 있습니다.");

    private final int status;
    private final String code;
    private final String message;
}
