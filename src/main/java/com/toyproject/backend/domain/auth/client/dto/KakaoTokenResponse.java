package com.toyproject.backend.domain.auth.client.dto;

/**
 * [4단계 · G] 카카오 토큰 응답 DTO — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code POST https://kauth.kakao.com/oauth/token} 응답 매핑. 인가 코드를 교환한 결과다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li><b>record</b> + {@code @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)}로
 *       카카오의 snake_case를 camelCase 컴포넌트에 매핑한다.</li>
 *   <li>Spring Boot 기본 설정이 미지의 필드를 무시하므로 카카오가 필드를 추가해도 깨지지 않는다.</li>
 * </ul>
 *
 * <p><b>예정 컴포넌트</b>: {@code accessToken}, {@code tokenType}, {@code refreshToken},
 * {@code expiresIn}, {@code scope}
 *
 * <p><b>주의</b><br>
 * 여기 담긴 {@code accessToken}·{@code refreshToken}은 <b>카카오의 토큰</b>이지 우리 서비스의 토큰이 아니다.
 * {@code accessToken}은 곧바로 {@code fetchUserInfo} 호출에만 쓰고 저장하지 않는다.
 * 카카오 {@code refreshToken}은 사용처가 없다(연결 해제는 Admin Key로 한다).
 */
public record KakaoTokenResponse(/* 구현 단계에서 정의 */) {
}
