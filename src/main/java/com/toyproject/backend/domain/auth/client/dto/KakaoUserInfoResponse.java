package com.toyproject.backend.domain.auth.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * [Phase 2 · 4단계] 카카오 사용자 정보({@code /v2/user/me}) 응답. 회원 생성·조회의 원천이다.
 *
 * <p>이메일·닉네임은 동의 항목이라 거부하면 {@code kakao_account}가 통째로 내려오지 않는다.
 * email이 없다고 가입을 실패시키지 않는다 — {@code null}로 두고 진행한다.
 * <p>{@code @JsonNaming}은 클래스 단위라 중첩 record에도 각각 붙인다.
 *
 * @param kakaoAccount 통째로 {@code null}일 수 있다
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KakaoUserInfoResponse(

        Long id,
        KakaoAccount kakaoAccount
) {

    /** provider 중립적인 {@code String}으로 저장한다 — 다른 제공자는 숫자가 아니다. */
    public String providerId() {
        return id == null ? null : String.valueOf(id);
    }

    /** 미동의면 {@code null}이고 그것이 정상 경로다 — 호출부가 email 없이 가입시킨다. */
    public String resolveEmail() {
        return kakaoAccount == null ? null : kakaoAccount.email();
    }

    /** {@code User.name}의 초기값. 3단 중 어느 단계가 비어도 안전하다. */
    public String resolveNickname() {
        if (kakaoAccount == null || kakaoAccount.profile() == null) {
            return null;
        }
        return kakaoAccount.profile().nickname();
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record KakaoAccount(String email, Profile profile) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Profile(String nickname) {
    }
}
