package com.toyproject.backend.domain.auth.token;

import com.toyproject.backend.domain.auth.config.JwtProperties;
import com.toyproject.backend.domain.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * [Phase 2 · 5단계] access token을 서명·발급하고 들어온 토큰의 claim을 꺼낸다.
 *
 * <p>refresh token은 JWT가 아니라 여기서 다루지 않는다(ADR 0005).
 * <p>키는 생성자에서 한 번 만들어 재사용한다.
 */
@Component
public class JwtProvider {

    /** 권한 claim 이름. 필터/테스트가 같은 키를 참조하도록 공개한다. */
    public static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long accessExpirationMillis;

    /**
     * @throws io.jsonwebtoken.security.WeakKeyException
     *         {@code jwt.secret}이 32바이트 미만일 때. 기동에서 드러난다(ADR 0022)
     */
    public JwtProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMillis = properties.accessExpiration();
    }

    /**
     * 로그인 세션의 access token을 발급한다. {@code subject}는 userId다.
     *
     * <p>{@code role} claim은 참고용이다. 실제 권한 판정은 매 요청 DB에서 다시 읽는다(ADR 0021).
     */
    public String createAccessToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessExpirationMillis);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * 서명·만료를 검증하고 payload를 반환한다. {@code getSubject()}가 userId다.
     *
     * <p>예외를 삼키거나 뭉개지 않는다 — 필터가 만료와 위조를 가를 근거를 잃는다.
     *
     * @throws ExpiredJwtException 만료된 토큰 (A004)
     * @throws JwtException        서명 위조·형식 오류·빈 값 (A003)
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** ms → 초 변환의 유일한 지점이다. 호출부에서 다시 나누면 만료가 1000배 짧아진다. */
    public long getAccessExpirationSeconds() {
        return accessExpirationMillis / 1000;
    }
}
