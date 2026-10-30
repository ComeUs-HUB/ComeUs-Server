package com.toyproject.backend.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyproject.backend.domain.auth.exception.AuthErrorCode;
import com.toyproject.backend.global.exception.ErrorCode;
import com.toyproject.backend.global.response.ApiResponse;
import com.toyproject.backend.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * [Phase 2 · 6단계] 인증 실패 응답을 전담한다. 401뿐 아니라 403(A008)도 여기서 나간다.
 *
 * <p>필터가 남긴 attribute에서 {@code ErrorCode}를 읽고, 없으면 A001로 폴백한다.
 * <p>{@code JwtAccessDeniedHandler}와의 경계는 ADR 0010. 응답 조립 방식을 일부러 맞춰 두었다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = resolveErrorCode(request);

        // 401을 박으면 A008(403)이 401로 뭉개진다.
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse error = new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), null);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(error));
    }

    /**
     * 필터가 남긴 사유를 꺼낸다.
     *
     * <p>attribute가 없는 경우는 토큰 자체를 보내지 않은 요청이다(필터가 익명 통과시킨 뒤
     * 인가 단계에서 거절됨). 이때는 A001이 맞다.
     */
    private ErrorCode resolveErrorCode(HttpServletRequest request) {
        Object attribute = request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE);
        return (attribute instanceof ErrorCode errorCode) ? errorCode : AuthErrorCode.UNAUTHORIZED;
    }
}
