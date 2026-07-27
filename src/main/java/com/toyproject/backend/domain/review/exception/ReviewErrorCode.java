package com.toyproject.backend.domain.review.exception;

import com.toyproject.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "리뷰를 찾을 수 없습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "R002", "이미 리뷰를 작성한 상품입니다."),
    REVIEW_NOT_ALLOWED(HttpStatus.FORBIDDEN, "R003", "배송 완료된 주문에 한해 리뷰를 작성할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
