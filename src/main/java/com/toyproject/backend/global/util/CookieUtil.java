package com.toyproject.backend.global.util;

import com.toyproject.backend.global.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * [Phase 2 · 8단계] {@code refresh_token}·{@code oauth_state} 두 쿠키를 만드는 유일한 지점.
 *
 * <p>공통 속성은 {@link #base}가 적용하고 삭제도 같은 헬퍼를 지난다 — 발급과 같은
 * {@code Path}를 주지 않으면 쿠키가 지워지지 않는다.
 * <p>이름 상수는 {@code static}이어야 한다 — {@code @CookieValue}의 속성이라 컴파일 타임 상수다.
 * <p>속성 표와 함정 전체는 ADR 0006. 계약은 {@code docs/domains/auth.md}.
 */
@Component
@RequiredArgsConstructor
public class CookieUtil {

    /** 재발급·로그아웃의 자격증명. 원문이 담긴다. */
    public static final String REFRESH_TOKEN = "refresh_token";

    /** 인가 왕복의 CSRF 방어값. 콜백에서 1회 대조 후 폐기된다. */
    public static final String OAUTH_STATE = "oauth_state";

    /** 재발급·로그아웃·탈퇴가 모두 이 아래다. 다른 요청에 실려 노출 지점을 늘리지 않는다. */
    private static final String REFRESH_TOKEN_PATH = "/api/auth";

    /** 쓰이는 곳이 콜백 하나뿐이라 거기까지 좁힌다(ADR 0006). */
    private static final String OAUTH_STATE_PATH = "/api/auth/kakao/callback";

    /** 인가 왕복에만 필요한 값이라 짧게 잡는다. 사용자가 카카오 화면에 머무는 시간을 감안한 값이다. */
    private static final Duration OAUTH_STATE_MAX_AGE = Duration.ofMinutes(5);

    private final AppProperties appProperties;

    /**
     * @param rawRefreshToken 토큰 원문. DB에 저장하는 해시가 아니다
     * @param maxAge          {@code RefreshToken.expiresAt}과 같은 설정값에서 나와야 한다 —
     *                        어긋나면 쿠키는 살아 있는데 서버가 거절한다
     */
    public ResponseCookie refreshTokenCookie(String rawRefreshToken, Duration maxAge) {
        return base(REFRESH_TOKEN, rawRefreshToken, REFRESH_TOKEN_PATH, maxAge, "Strict");
    }

    /** 로그아웃·탈퇴 시 브라우저에서 실제로 지워지도록 {@code Path}까지 맞춰 내린다. */
    public ResponseCookie deleteRefreshTokenCookie() {
        return base(REFRESH_TOKEN, "", REFRESH_TOKEN_PATH, Duration.ZERO, "Strict");
    }

    public ResponseCookie oauthStateCookie(String state) {
        return base(OAUTH_STATE, state, OAUTH_STATE_PATH, OAUTH_STATE_MAX_AGE, "Lax");
    }

    /** 대조 직후 성공·실패 모두 내린다. 남겨두면 뒤로가기로 콜백을 재사용할 수 있다. */
    public ResponseCookie deleteOauthStateCookie() {
        return base(OAUTH_STATE, "", OAUTH_STATE_PATH, Duration.ZERO, "Lax");
    }

    /** {@code domain}을 설정하지 않아 host-only다 — 상위 도메인에 걸면 서브도메인에도 실린다. */
    private ResponseCookie base(String name, String value, String path, Duration maxAge, String sameSite) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(appProperties.cookie().secure())
                .path(path)
                .sameSite(sameSite)
                .maxAge(maxAge)
                .build();
    }
}
