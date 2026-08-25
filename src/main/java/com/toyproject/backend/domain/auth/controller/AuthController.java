package com.toyproject.backend.domain.auth.controller;

import com.toyproject.backend.domain.auth.config.KakaoProperties;
import com.toyproject.backend.domain.auth.dto.KakaoLoginResult;
import com.toyproject.backend.domain.auth.dto.TokenResponse;
import com.toyproject.backend.domain.auth.exception.AuthErrorCode;
import com.toyproject.backend.domain.auth.service.AuthService;
import com.toyproject.backend.global.config.AppProperties;
import com.toyproject.backend.global.exception.BusinessException;
import com.toyproject.backend.global.exception.CommonErrorCode;
import com.toyproject.backend.global.response.ApiResponse;
import com.toyproject.backend.global.util.CookieUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * [8단계 · K] 인증 API
 *
 * <p><b>역할</b><br>
 * 로그인 왕복(302·쿠키)과 토큰 재발급·로그아웃의 HTTP 계약을 담당한다.
 * 비즈니스 판단은 전부 {@code AuthService}에 있고, 여기서는 <b>무엇을 쿠키로 심고 어디로 보낼지</b>만 정한다.
 *
 * <p><b>API</b>
 * <table border="1">
 *   <tr><th>Method</th><th>URI</th><th>요청</th><th>응답</th></tr>
 *   <tr><td>GET</td><td>{@code /api/auth/kakao/authorize}</td><td>-</td>
 *       <td><b>302</b> → 카카오 + {@code Set-Cookie: oauth_state}</td></tr>
 *   <tr><td>GET</td><td>{@code /api/auth/kakao/callback}</td><td>{@code ?code=&state=}(카카오가 채움)</td>
 *       <td><b>302</b> → FE + {@code Set-Cookie: refresh_token}</td></tr>
 *   <tr><td>POST</td><td>{@code /api/auth/refresh}</td><td>없음(쿠키)</td>
 *       <td>{@code {accessToken, tokenType, accessTokenExpiresIn}} — {@code Set-Cookie} 없음</td></tr>
 *   <tr><td>POST</td><td>{@code /api/auth/logout}</td><td>없음(쿠키)</td>
 *       <td>{@code null} + 쿠키 만료 {@code Set-Cookie}</td></tr>
 * </table>
 *
 * <p><b>설계</b>
 * <ul>
 *   <li><b>{@code /kakao/authorize}·{@code /kakao/callback}은 JSON을 반환하지 않는다. 항상 302다.</b>
 *       브라우저 네비게이션이라 {@code ApiResponse} 포맷이 사용자 화면에 노출되면 안 된다.</li>
 *   <li><b>콜백은 실패해도 예외를 던지지 않는다.</b> {@code {FE}/oauth/complete?error={code}}로 302해
 *       <b>FE가 에러 UI를 소유</b>하게 한다. A005·A006·A009가 이 경로로 전달된다.
 *       반대로 {@code /refresh}·{@code /logout}은 XHR이라 예외를 그대로 던져
 *       {@code GlobalExceptionHandler}가 공통 포맷으로 응답하게 둔다.</li>
 *   <li>쿠키 값은 {@link CookieUtil}에서만 만든다.</li>
 * </ul>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li><b>응답 {@code data}에 {@code refreshToken}을 절대 포함하지 않는다</b> — 오직 {@code Set-Cookie}로만.</li>
 *   <li><b>어떤 응답에도 access token을 URL·쿼리스트링에 싣지 않는다.</b></li>
 *   <li>재발급 응답에는 <b>{@code Set-Cookie}가 없다</b>(rotation 미적용).</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String FRONTEND_COMPLETE_PATH = "/oauth/complete";

    private final AuthService authService;
    private final KakaoProperties kakaoProperties;
    private final AppProperties appProperties;
    private final CookieUtil cookieUtil;

    /**
     * 로그인 시작 — state를 심고 카카오 인가 화면으로 보낸다.
     *
     * <p>FE는 이 주소로 <b>페이지 이동</b>해야 한다. {@code fetch}로 부르면 302를 따라가 버려
     * 카카오 로그인 화면이 뜨지 않는다.
     */
    @GetMapping("/kakao/authorize")
    public ResponseEntity<Void> kakaoAuthorize() {
        String state = authService.generateOAuthState();

        return redirect(kakaoAuthorizeUri(state), cookieUtil.oauthStateCookie(state));
    }

    /**
     * 카카오가 인가 코드를 들고 되돌아오는 착지점. <b>사용자가 아니라 카카오가 호출한다.</b>
     *
     * <ol>
     *   <li>카카오가 {@code ?error=}를 실어 보낸 경우(동의 거부 등) → 그대로 FE로 302</li>
     *   <li>{@code oauth_state} 쿠키와 쿼리 {@code state} 대조 → 불일치·누락이면 A009</li>
     *   <li><b>{@code oauth_state} 쿠키 즉시 삭제</b>(1회용). 성공·실패 어느 쪽이든 내린다</li>
     *   <li>{@code authService.kakaoLogin(code)}</li>
     *   <li>refresh 원문을 {@code refresh_token} 쿠키로 설정</li>
     *   <li>{@code {app.frontend.base-url}/oauth/complete}로 302. 신규 가입이면 {@code ?isNewUser=true}</li>
     * </ol>
     *
     * <p>모든 파라미터가 {@code required = false}인 이유: 값이 없으면 Spring이 400을 내는데,
     * 그러면 <b>브라우저 화면에 우리 에러 응답이 그대로 노출된다.</b> 직접 받아서 FE로 302시킨다.
     */
    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(name = "error", required = false) String kakaoError,
            @CookieValue(name = CookieUtil.OAUTH_STATE, required = false) String stateCookie) {

        // Oauth_state 명시적 삭제 (max = Duration_ZERO)
        ResponseCookie stateCleanup = cookieUtil.deleteOauthStateCookie();

        if (StringUtils.hasText(kakaoError)) {
            log.info("카카오 인가 거부 — error={}", kakaoError);
            return redirect(frontendUri("error", kakaoError), stateCleanup);
        }

        if (!matchesState(state, stateCookie)) {
            return redirect(frontendUri("error", AuthErrorCode.INVALID_OAUTH_STATE.getCode()), stateCleanup);
        }

        if (!StringUtils.hasText(code)) {
            log.warn("state는 유효한데 인가 코드가 없다 — 카카오 응답 형식 확인 필요");
            return redirect(frontendUri("error", AuthErrorCode.KAKAO_AUTH_FAILED.getCode()), stateCleanup);
        }

        try {
            KakaoLoginResult result = authService.kakaoLogin(code);
            String location = result.isNewUser()
                    ? frontendUri("isNewUser", "true")
                    : frontendCompleteUri();

            return redirect(location, stateCleanup,
                    cookieUtil.refreshTokenCookie(result.rawRefreshToken(), authService.getRefreshTokenMaxAge()));

        } catch (BusinessException e) {
            log.warn("카카오 로그인 실패 — code={}", e.getErrorCode().getCode());
            return redirect(frontendUri("error", e.getErrorCode().getCode()), stateCleanup);

        } catch (Exception e) {
            // 컨트롤러의 광범위 catch는 보통 냄새지만, 여기서는 "브라우저에 JSON을 절대 내지 않는다"가
            // 계약이다. 이걸 놓치면 DB 장애(커넥션 타임아웃 등)가 GlobalExceptionHandler까지 올라가
            // ApiResponse JSON 500이 주소창에 그대로 렌더링된다.
            log.error("카카오 로그인 처리 중 예기치 못한 오류", e);
            return redirect(frontendUri("error", CommonErrorCode.INTERNAL_SERVER_ERROR.getCode()), stateCleanup);
        }
    }

    /**
     * access token 재발급. FE의 앱 부팅·401 인터셉터가 부르는 <b>최다 호출 엔드포인트</b>다.
     *
     * <p>rotation을 하지 않으므로 <b>응답에 {@code Set-Cookie}가 없다.</b> 여러 탭이 동시에 불러도 전부 성공한다.
     *
     * <p>쿠키가 없으면 {@code null}이 넘어가고 {@code AuthService}가 A007로 거절한다 —
     * 미로그인 상태에서의 정상적인 실패 경로이므로 여기서 따로 분기하지 않는다.
     */
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(
            @CookieValue(name = CookieUtil.REFRESH_TOKEN, required = false) String rawRefreshToken) {

        return ApiResponse.success(authService.reissue(rawRefreshToken));
    }

    /**
     * 로그아웃 — 해당 기기의 토큰만 무효화하고 쿠키를 만료시킨다.
     *
     * <p>인증이 필요 없다({@code permitAll}). access token이 만료된 상태에서도 로그아웃은 되어야 한다.
     *
     * <p>토큰이 이미 없어도 <b>200이다.</b> {@code AuthService.logout}이 멱등하므로 실패로 다룰 이유가 없고,
     * 쿠키 만료는 어느 쪽이든 내려야 브라우저에서 실제로 지워진다.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = CookieUtil.REFRESH_TOKEN, required = false) String rawRefreshToken) {

        authService.logout(rawRefreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.deleteRefreshTokenCookie().toString())
                .body(ApiResponse.success(null));
    }

    /**
     * 카카오 인가 URL을 조립한다.
     *
     * <p>⚠️ <b>{@code UriComponentsBuilder}를 쓰지 않는다.</b> 그쪽의 {@code encode()}는 query 값의
     * {@code :}·{@code /}를 RFC 3986상 허용 문자로 보고 남겨두는데, 카카오는 등록된 Redirect URI와
     * <b>문자열로 대조</b>하므로 percent-encoding이 정확히 맞아야 한다. {@code URLEncoder}로 직접 인코딩한다.
     */
    private String kakaoAuthorizeUri(String state) {
        return kakaoProperties.authorizeUri()
                + "?client_id=" + encode(kakaoProperties.clientId())
                + "&redirect_uri=" + encode(kakaoProperties.redirectUri())
                + "&response_type=code"
                + "&state=" + encode(state);
    }

    /**
     * 쿠키의 state와 카카오가 되돌려준 state를 대조한다.
     *
     * <p>{@code MessageDigest.isEqual}로 <b>상수 시간 비교</b>한다. {@code equals}는 첫 불일치 문자에서
     * 즉시 반환해 비교 시간이 일치 길이에 비례하므로, 이론상 state를 한 글자씩 맞춰갈 여지를 준다.
     *
     * <p>쿠키가 없는 경우가 실무에서 가장 흔한 실패다 — {@code oauth_state}가 {@code Lax}가 아니면
     * cross-site 네비게이션인 콜백에 쿠키가 실려오지 않아 <b>로그인이 전부 여기서 막힌다.</b>
     * 그래서 누락과 불일치를 로그에서 구분한다.
     */
    private boolean matchesState(String state, String stateCookie) {
        if (!StringUtils.hasText(state) || !StringUtils.hasText(stateCookie)) {
            log.warn("oauth_state 누락 — query 존재={}, cookie 존재={} (쿠키가 없으면 SameSite 설정을 먼저 의심할 것)",
                    StringUtils.hasText(state), StringUtils.hasText(stateCookie));
            return false;
        }

        boolean matched = MessageDigest.isEqual(
                state.getBytes(StandardCharsets.UTF_8), stateCookie.getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            log.warn("oauth_state 불일치 — CSRF 시도이거나 콜백 재사용(뒤로가기)이다");
        }
        return matched;
    }

    private String frontendCompleteUri() {
        return appProperties.frontend().baseUrl() + FRONTEND_COMPLETE_PATH;
    }

    /** FE 복귀 URL에 파라미터 하나를 붙인다. 값이 에러 코드·{@code "true"}뿐이라 표준 인코딩으로 충분하다. */
    private String frontendUri(String name, String value) {
        return UriComponentsBuilder.fromUriString(frontendCompleteUri())
                .queryParam(name, value)
                .build()
                .toUriString();
    }

    /** 302 + {@code Set-Cookie} 조립. 쿠키는 개수만큼 헤더를 따로 붙인다(한 헤더에 합칠 수 없다). */
    private ResponseEntity<Void> redirect(String location, ResponseCookie... cookies) {
        ResponseEntity.HeadersBuilder<?> builder = ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(location));

        for (ResponseCookie cookie : cookies) {
            builder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return builder.build();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
