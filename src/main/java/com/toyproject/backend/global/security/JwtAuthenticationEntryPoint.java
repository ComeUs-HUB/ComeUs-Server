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
 * [6단계 · H] 인증 실패 응답
 *
 * <p><b>역할</b><br>
 * <b>인증 실패 응답을 전담하는 단 하나의 주체.</b> 401뿐 아니라 <b>403({@code WITHDRAWN_USER})도
 * 여기서 나간다.</b>
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>{@link JwtAuthenticationFilter}가 남긴 request attribute에서 {@code ErrorCode}를 읽는다.
 *       없으면 {@code UNAUTHORIZED}(A001)로 폴백한다(토큰 없이 보호 자원에 접근한 경우).</li>
 *   <li>필터 단계라 {@code GlobalExceptionHandler}를 타지 않는다. 공통 {@code ApiResponse} 포맷을
 *       {@code ObjectMapper}로 <b>직접 직렬화</b>한다. Boot가 구성해 둔 Bean을 주입받아
 *       컨트롤러 응답과 직렬화 설정이 어긋나지 않게 한다.</li>
 *   <li>{@code Content-Type: application/json} + {@code charset=UTF-8}을 명시한다(한글 메시지가 깨진다).</li>
 * </ul>
 *
 * <p><b>{@code AccessDeniedHandler}를 두지 않는 근거</b><br>
 * {@link JwtAuthenticationFilter}가 예외 시 {@code SecurityContext}를 비운 채 체인을 통과시키므로,
 * Spring Security는 이를 <b>미인증(anonymous)</b>으로 보고 항상 {@code AuthenticationEntryPoint}를 호출한다.
 * {@code AccessDeniedHandler}는 <i>인증은 됐는데 권한이 부족한</i> 경우에만 불리므로 현재 설계에는
 * 도달 경로가 없다. ADMIN 권한 체크가 들어오는 phase6에서 재검토한다.
 *
 * <p><b>주의</b><br>
 * ⚠️ HTTP 상태를 <b>하드코딩하지 말고 {@code errorCode.getHttpStatus()}를 쓴다.</b>
 * 이 EntryPoint는 403(A008)까지 함께 내리므로, {@code 401}을 박아두면 A008이 401로 뭉개져
 * "탈퇴 회원"과 "인증 필요"를 클라이언트가 구분할 수 없게 된다.
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

        // ⚠️ 401 하드코딩 금지. A008(403)이 여기로 함께 나간다.
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse error = new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), null);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(error));
    }

    /**
     * 필터가 남긴 사유를 꺼낸다.
     *
     * <p>attribute가 없는 경우는 <b>토큰 자체를 보내지 않은 요청</b>이다(필터가 익명 통과시킨 뒤
     * 인가 단계에서 거절됨). 이때는 A001이 맞다.
     */
    private ErrorCode resolveErrorCode(HttpServletRequest request) {
        Object attribute = request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE);
        return (attribute instanceof ErrorCode errorCode) ? errorCode : AuthErrorCode.UNAUTHORIZED;
    }
}
