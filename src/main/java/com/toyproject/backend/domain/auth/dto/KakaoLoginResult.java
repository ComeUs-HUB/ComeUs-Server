package com.toyproject.backend.domain.auth.dto;

/**
 * [Phase 2 · 8단계] 로그인 결과. {@code AuthService}가 컨트롤러에 넘기는 전달 객체이고
 * 외부 응답 바디가 아니다.
 *
 * <p>서비스가 HTTP를 모르게 하는 경계 타입이다 — 쿠키로 심을지 URL에 붙일지는 컨트롤러가 정한다.
 *
 * @param rawRefreshToken 응답 바디·URL·로그에 실리지 않는다. {@code Set-Cookie}로만 쓴다
 * @param isNewUser       FE 복귀 URL의 {@code ?isNewUser=true}가 된다
 */
public record KakaoLoginResult(String rawRefreshToken, boolean isNewUser) {
}
