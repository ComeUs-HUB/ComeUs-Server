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
 * [5단계 · I] Access Token 생성·파싱
 *
 * <p><b>역할</b><br>
 * jjwt 0.12.x로 access token을 서명·발급하고, 들어온 토큰을 검증해 claim을 꺼낸다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li><b>access token만 담당한다.</b> refresh token은 JWT가 아니므로 여기서 다루지 않는다
 *       (생성은 {@link SecureTokenGenerator}).</li>
 *   <li>키는 생성자에서 한 번만 만들어 재사용한다. 매 호출 생성하면 불필요한 비용이 든다.</li>
 *   <li>{@code User} 엔티티를 인자로 받으므로 의존 방향은 {@code auth → user} 단방향으로 유지된다.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * {@link #parseClaims}는 예외를 <b>삼키거나 뭉개지 않고 그대로 던진다.</b>
 * {@code ExpiredJwtException}(만료)과 그 밖의 {@code JwtException}(서명 위조·형식 오류)을
 * 6단계 필터가 각각 A004/A003으로 구분해야 하는데, 여기서 {@code Optional}이나 boolean으로 뭉개면
 * 그 구분이 영영 불가능해진다.
 *
 * <p><b>⚠️ 결정 필요 — ms → 초 변환의 소유자</b><br>
 * {@code TokenResponse}(8단계)의 설계 노트에도 "{@code accessTokenExpiresIn}은 초 단위로 내린다
 * (설정값은 ms라 변환이 필요하다)"고 적혀 있어 변환 지점이 두 곳으로 읽힌다.
 * <b>여기 {@link #getAccessExpirationSeconds()}가 유일한 변환 지점</b>으로 두었으니,
 * 8단계에서 {@code TokenResponse}가 다시 1000으로 나누지 않도록 할 것(이중 변환 시 만료가 1000배 짧아진다).
 */
@Component
public class JwtProvider {

    /** 권한 claim 이름. 필터/테스트가 같은 키를 참조하도록 공개한다. */
    public static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long accessExpirationMillis;

    /**
     * @throws io.jsonwebtoken.security.WeakKeyException {@code jwt.secret}이 32바이트 미만인 경우.
     *         빈 생성 시점에 터지므로 설정 실수가 <b>앱 기동에서</b> 드러난다(fail-fast).
     *         이 동작의 확정 여부는 {@link JwtProperties} 문서의 "결정 필요" 참고
     */
    public JwtProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMillis = properties.accessExpiration();
    }

    /**
     * 로그인 세션의 access token을 발급한다.
     *
     * <p>{@code subject}에 userId를 넣는다 — 6단계 {@code CustomUserDetailsService}가
     * 이 값을 {@code loadUserByUsername}의 인자로 받아 DB를 조회한다.
     *
     * <p>{@code role} claim은 <b>참고용</b>이다. 실제 권한 판정은 매 요청 DB에서 다시 읽으므로
     * (탈퇴·권한 변경을 즉시 반영하기 위함), 이 claim만 믿고 인가를 결정하지 않는다.
     *
     * @param user 영속화된 회원. {@code id}가 채워져 있어야 한다
     * @return 서명된 JWT 문자열
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
     * 토큰의 서명·만료를 검증하고 payload를 반환한다.
     *
     * <p>⚠️ <b>예외를 잡지 않는다.</b> 호출부(6단계 {@code JwtAuthenticationFilter})가
     * {@code ExpiredJwtException}을 <b>먼저</b> catch해 A004로, 나머지 {@code JwtException}을
     * A003으로 나눈다. {@code ExpiredJwtException}이 {@code JwtException}의 하위 타입이라
     * catch 순서가 뒤집히면 A004가 A003으로 뭉개진다.
     *
     * @param token {@code Authorization: Bearer} 헤더에서 꺼낸 JWT
     * @return 검증된 payload. {@code getSubject()}가 userId다
     * @throws ExpiredJwtException 만료된 토큰 (→ A004)
     * @throws JwtException        서명 위조·형식 오류·빈 값 등 (→ A003)
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * {@code POST /api/auth/refresh} 응답의 {@code accessTokenExpiresIn}(초).
     *
     * <p>설정값이 ms라 여기서 한 번만 초로 바꾼다. 위 "결정 필요" 참고 — 호출부에서 다시 나누지 말 것.
     */
    public long getAccessExpirationSeconds() {
        return accessExpirationMillis / 1000;
    }
}
