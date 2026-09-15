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
 * [Phase 3 · 1단계] 인가 실패 응답
 *
 * <p><b>역할</b><br>
 * <b>인증은 됐으나 권한이 부족한</b> 요청에 403을 공통 {@code ApiResponse} 포맷으로 내보낸다.
 * 로그인한 USER가 ADMIN 전용 엔드포인트를 호출한 경우가 유일한 도달 경로다.
 *
 * <p><b>왜 Phase 3에서 생겼나</b><br>
 * Phase 2까지는 이 핸들러를 두지 않았고, 그 근거는 <b>"도달 경로가 없다"</b>였다
 * ({@link JwtAuthenticationEntryPoint} 참고). {@code SecurityConfig}가
 * {@code anyRequest().authenticated()}만 걸어 "로그인했는가"까지만 판정했기 때문에,
 * 인가 단계에서 거절되는 요청 자체가 존재하지 않았다.
 * 카테고리 CRUD에 {@code hasRole("ADMIN")}이 붙으면서 그 전제가 깨진다 —
 * 예고돼 있던 재검토 시점이 여기다.
 *
 * <p><b>이 클래스가 없으면</b> Spring Security 기본 핸들러가 <b>바디 없는 403</b>을 내보내
 * {@code {success, data, error}} 포맷이 깨진다. 클라이언트는 {@code error.code}를 읽지 못하고,
 * 401과 403의 응답 모양이 서로 달라진다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>{@link JwtAuthenticationEntryPoint}와 <b>같은 방식으로 쓴다</b> — 주입받은 {@code ObjectMapper}로
 *       직접 직렬화하고, {@code charset=UTF-8}을 명시하며(한글 메시지가 깨진다),
 *       상태 코드는 {@code errorCode.getHttpStatus()}에서 가져온다. 두 응답의 모양이 어긋나면
 *       필터 단계 응답을 두 곳에서 만드는 의미가 없어진다.</li>
 *   <li>사유를 request attribute로 주고받지 않는다. EntryPoint는 401·403을 갈라야 해서 필터가 남긴
 *       사유가 필요했지만, 여기 도달하는 실패는 <b>"권한 부족" 하나뿐</b>이라 분기가 없다.
 *       {@code AuthErrorCode.FORBIDDEN}(A002)을 고정으로 쓴다 — 등록만 돼 있고
 *       던지는 곳이 없던 코드가 여기서 처음 쓰인다.</li>
 * </ul>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>⚠️ <b>이 핸들러를 등록해도 A001·A003·A004·A008의 동작은 바뀌지 않는다.</b>
 *       {@code ExceptionTranslationFilter}는 인증 주체가 익명이면 {@code AccessDeniedException}이어도
 *       {@code AccessDeniedHandler}가 아니라 {@code AuthenticationEntryPoint}를 호출한다.
 *       {@code JwtAuthenticationFilter}는 실패 시 {@code SecurityContext}를 비우므로
 *       인증 실패는 전부 익명으로 취급되어 지금처럼 EntryPoint로 간다.</li>
 *   <li>{@code WITHDRAWN_USER}(A008)도 403이지만 <b>여기서 나가지 않는다.</b> 탈퇴는 인증 단계에서
 *       걸러져 EntryPoint가 처리한다. 같은 상태 코드를 두 곳이 내보내는 것은 의도된 것이고,
 *       코드(A002/A008)로 구분된다.</li>
 * </ul>
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
