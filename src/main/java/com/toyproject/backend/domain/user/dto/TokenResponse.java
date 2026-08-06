package com.toyproject.backend.domain.user.dto;

/**
 * [8단계 · K] 토큰 응답 DTO — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code POST /api/auth/refresh}의 응답 바디. FE는 이 값을 <b>메모리(JS 변수)에만</b> 보관한다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>record. {@code tokenType}을 {@code "Bearer"}로 채우는 정적 팩터리를 둔다.</li>
 *   <li>{@code accessTokenExpiresIn}은 <b>초 단위</b>로 내린다(설정값은 ms라 변환이 필요하다).</li>
 * </ul>
 *
 * <p><b>예정 컴포넌트</b>: {@code accessToken}, {@code tokenType}, {@code accessTokenExpiresIn}
 *
 * <p><b>주의</b><br>
 * <b>{@code refreshToken}을 절대 포함하지 않는다.</b> refresh token은 오직 {@code HttpOnly} 쿠키로만 오간다.
 * 여기에 넣는 순간 JS가 읽을 수 있게 되어 {@code HttpOnly}로 얻은 방어가 통째로 무의미해진다.
 * ({@code localStorage}/{@code sessionStorage}에도 어떤 토큰도 저장하지 않는다.)
 */
public record TokenResponse(/* 구현 단계에서 정의 */) {
}
