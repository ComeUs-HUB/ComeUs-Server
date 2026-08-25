package com.toyproject.backend.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * [5단계 · I] JWT 설정
 *
 * <p><b>역할</b><br>
 * {@code jwt.*} 설정 묶음. {@code JwtProvider}(서명·만료)와 {@code AuthService}·{@code CookieUtil}
 * (refresh 수명) 양쪽에서 참조한다. 세 곳 이상에서 쓰이므로 {@code @Value} 산발 주입 대신 타입으로 묶는다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>record이므로 생성자 바인딩으로 동작한다.
 *       {@code BackendApplication}의 {@code @ConfigurationPropertiesScan}이 찾아 등록한다.</li>
 *   <li><b>{@code @Validated}/{@code @NotBlank}를 붙이지 않는다.</b> {@code KakaoProperties}와 같은 이유다
 *       — 테스트 yml이 main {@code application.yml}을 shadow하므로 검증을 걸면 프로퍼티가 빠진
 *       테스트 컨텍스트가 깨진다. (secret 길이 검증은 {@code JwtProvider} 생성자가 사실상 대신한다.
 *       아래 "결정 필요" 참고.)</li>
 *   <li>단위는 ms {@code long} <b>그대로</b> 둔다. 초·{@code Duration} 변환은 쓰는 쪽이 한다.</li>
 * </ul>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>{@code secret}은 <b>최소 32바이트</b>여야 한다. 짧으면 키 생성 시점에 예외가 난다.<br>
 *       ⚠️ <b>서명 알고리즘은 키 길이가 정한다.</b> {@code Keys.hmacShaKeyFor}가 32바이트 이상이면 HS256,
 *       48바이트 이상이면 HS384, 64바이트 이상이면 HS512를 고른다. 코드로 지정하는 값이 아니므로
 *       <b>운영과 로컬의 시크릿 길이가 다르면 서명 알고리즘도 달라진다.</b></li>
 *   <li>{@code refreshExpiration}은 <b>JWT의 {@code exp}가 아니다.</b> refresh token은 JWT가 아니므로,
 *       이 값은 {@code RefreshToken.expiresAt} 계산과 쿠키 {@code Max-Age}에만 쓰인다.</li>
 * </ul>
 *
 * <p><b>⚠️ 결정 필요 — {@code secret} 길이 미달 시 실패 시점</b><br>
 * 현재는 {@code JwtProvider} 생성자에서 키를 만들므로 <b>빈 생성 시점 = 앱 기동 시 fail-fast</b>다.
 * 설정이 잘못된 채 배포되면 첫 로그인이 아니라 기동에서 즉시 드러난다.
 * 이 동작을 확정할지, 아니면 여기 {@code @Validated + @Size(min = 32)}로 옮겨 실패 메시지를
 * 더 친절하게 만들지 결정이 필요하다(후자는 위 shadow 문제 때문에 테스트 yml 관리 비용이 늘어난다).
 * {@code docs/SEQUENCE/phase2-auth.md}에 명시된 바가 없다.
 *
 * @param secret            HMAC 서명 키(최소 32바이트, 길이가 HS256/384/512를 결정한다).
 *                          운영에서는 환경변수 {@code JWT_SECRET}로 주입한다
 * @param accessExpiration  access token 유효 기간 (ms, 30분)
 * @param refreshExpiration refresh token 유효 기간 (ms, 7일). JWT {@code exp}가 아니다
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(

        String secret,
        long accessExpiration,
        long refreshExpiration
) {
}
