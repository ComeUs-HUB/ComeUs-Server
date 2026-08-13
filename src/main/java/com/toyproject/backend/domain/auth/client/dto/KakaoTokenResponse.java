package com.toyproject.backend.domain.auth.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * [4단계 · G] 카카오 토큰 응답 DTO
 *
 * <p><b>역할</b><br>
 * {@code POST https://kauth.kakao.com/oauth/token} 응답 매핑. 인가 코드를 교환한 결과다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>{@code @JsonNaming(SnakeCaseStrategy)}로 카카오의 snake_case를 camelCase 컴포넌트에 매핑한다.</li>
 *   <li>Jackson 기본 설정이 미지의 필드({@code id_token} 등)를 무시하므로
 *       카카오가 응답 필드를 추가해도 깨지지 않는다.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * 여기 담긴 {@code accessToken}·{@code refreshToken}은 <b>카카오의 토큰</b>이지 우리 서비스의 토큰이 아니다.
 *
 * @param accessToken  카카오 액세스 토큰. 곧바로 {@code fetchUserInfo} 호출에만 쓰고 <b>저장하지 않는다</b>
 * @param tokenType    항상 {@code "bearer"}. 사용처 없음
 * @param refreshToken 카카오 리프레시 토큰. <b>사용처가 없다</b> — 연결 해제는 Admin Key로 한다
 * @param expiresIn    카카오 액세스 토큰의 만료까지 남은 초. 사용처 없음
 * @param scope        사용자가 동의한 항목 목록. 사용처 없음(동의 여부는 사용자정보 응답의 필드 유무로 판단한다)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KakaoTokenResponse(

        String accessToken,
        String tokenType,
        String refreshToken,
        Integer expiresIn,
        String scope
) {
}
