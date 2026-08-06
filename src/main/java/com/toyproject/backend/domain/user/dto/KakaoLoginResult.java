package com.toyproject.backend.domain.user.dto;

/**
 * [8단계 · K] 카카오 로그인 결과 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code AuthService.kakaoLogin()}이 {@code AuthController}에 넘기는 내부 전달 객체.
 * 외부 응답 바디가 아니다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>record. 서비스가 HTTP를 모르게 하기 위한 경계 타입이다 — 서비스는 값만 돌려주고,
 *       쿠키로 심을지 URL에 붙일지는 컨트롤러가 결정한다.</li>
 * </ul>
 *
 * <p><b>예정 컴포넌트</b>
 * <ul>
 *   <li>{@code rawRefreshToken} — 컨트롤러가 {@code Set-Cookie}로만 사용</li>
 *   <li>{@code isNewUser} — 컨트롤러가 FE 복귀 URL의 {@code ?isNewUser=true}로 사용</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * {@code rawRefreshToken}은 응답 바디나 URL에 <b>절대</b> 실리지 않는다. 로그에도 남기지 않는다.
 */
public record KakaoLoginResult(/* 구현 단계에서 정의 */) {
}
