package com.toyproject.backend.domain.user.dto;

import com.toyproject.backend.domain.user.entity.User;

/**
 * [Phase 2 · 7단계] {@code UserService.findOrCreateKakaoUser}의 반환 타입.
 * HTTP 응답 바디가 아니며 엔티티를 담고 있어 컨트롤러 밖으로 나가지 않는다.
 *
 * <p>신규 여부를 아는 쪽은 회원을 만든 user 도메인뿐이다 — auth가 {@code createdAt} 비교 같은
 * 추측으로 판정하지 않도록 값으로 명시해 넘긴다.
 *
 * @param isNewUser 이번 호출에서 새로 만들었으면 {@code true}
 */
public record KakaoUserResolution(User user, boolean isNewUser) {

    /** 재활성화된 {@code WITHDRAWN} 계정도 여기다 — 가입 이력이 있으니 신규가 아니다. */
    public static KakaoUserResolution existing(User user) {
        return new KakaoUserResolution(user, false);
    }

    /** 이번 로그인에서 회원 레코드를 새로 만든 경우. */
    public static KakaoUserResolution created(User user) {
        return new KakaoUserResolution(user, true);
    }
}
