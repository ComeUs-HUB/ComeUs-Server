package com.toyproject.backend.domain.auth.dto;

/**
 * [8단계 · K] 카카오 로그인 결과
 *
 * <p><b>역할</b><br>
 * {@code AuthService.kakaoLogin()}이 {@code AuthController}에 넘기는 내부 전달 객체.
 * 외부 응답 바디가 아니다.
 *
 * <p><b>설계</b><br>
 * 서비스가 HTTP를 모르게 하기 위한 경계 타입이다 — 서비스는 값만 돌려주고,
 * 쿠키로 심을지 URL에 붙일지는 컨트롤러가 결정한다.
 *
 * <p><b>주의</b><br>
 * {@code rawRefreshToken}은 응답 바디나 URL에 <b>절대</b> 실리지 않는다. 로그에도 남기지 않는다.
 * 컨트롤러에서의 유일한 소비처는 {@code CookieUtil.refreshTokenCookie}다.
 *
 * @param rawRefreshToken 토큰 원문. 컨트롤러가 {@code Set-Cookie}로만 사용한다
 * @param isNewUser       이번 로그인에서 회원이 생성되었으면 {@code true}.
 *                        컨트롤러가 FE 복귀 URL의 {@code ?isNewUser=true}로 사용한다
 */
public record KakaoLoginResult(String rawRefreshToken, boolean isNewUser) {
}
