package com.toyproject.backend.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyproject.backend.domain.auth.exception.AuthErrorCode;
import com.toyproject.backend.global.exception.ErrorCode;
import com.toyproject.backend.global.response.ApiResponse;
import com.toyproject.backend.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * [Phase 3 · 1단계] 인증은 됐으나 권한이 부족한 요청에 403(A002)을 공통 포맷으로 내보낸다.
 *
 * <p>등록 근거와 익명 요청이 EntryPoint로 가는 이유는 ADR 0010.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode errorCode = AuthErrorCode.FORBIDDEN;

        // 인증된 사용자가 권한 밖을 두드린 것이라 정상 운영에서 드물다. 진단을 위해 남긴다.
        log.warn("인가 거절 — {} {}", request.getMethod(), request.getRequestURI());

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse error = new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), null);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(error));
    }
}
