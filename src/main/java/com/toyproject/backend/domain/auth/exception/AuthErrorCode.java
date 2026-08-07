package com.toyproject.backend.domain.auth.exception;

import com.toyproject.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A002", "접근 권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A004", "만료된 토큰입니다."),
    KAKAO_AUTH_FAILED(HttpStatus.BAD_REQUEST, "A005", "카카오 인증에 실패했습니다."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "A006", "카카오 서버 연동 중 오류가 발생했습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A007", "유효하지 않거나 만료된 리프레시 토큰입니다."),
    WITHDRAWN_USER(HttpStatus.FORBIDDEN, "A008", "탈퇴한 회원입니다."),
    INVALID_OAUTH_STATE(HttpStatus.BAD_REQUEST, "A009", "잘못된 인증 요청입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
