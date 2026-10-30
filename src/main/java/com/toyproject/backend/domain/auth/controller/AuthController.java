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
 * [Phase 2 · 8단계] 로그인 왕복(302·쿠키)과 재발급·로그아웃의 HTTP 계약.
 * 비즈니스 판단은 {@code AuthService}에 있고 여기서는 무엇을 심고 어디로 보낼지만 정한다.
 *
 * <p>인가·콜백은 JSON이 아니라 항상 302다. 브라우저 네비게이션이라 {@code ApiResponse}가
 * 주소창에 렌더링되면 안 된다 — 콜백은 실패해도 던지지 않고 {@code ?error=}로 302한다.
 * {@code /refresh}·{@code /logout}은 XHR이라 그대로 던져 공통 포맷으로 나간다.
 * <p>{@code refreshToken}은 응답 바디에, access token은 URL에 싣지 않는다.
 * <p>계약은 {@code docs/domains/auth.md}의 "API".
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
     * <p>FE는 페이지 이동으로 불러야 한다. {@code fetch}는 302를 따라가 로그인 화면이 뜨지 않는다.
     */
    @GetMapping("/kakao/authorize")
    public ResponseEntity<Void> kakaoAuthorize() {
        String state = authService.generateOAuthState();

        return redirect(kakaoAuthorizeUri(state), cookieUtil.oauthStateCookie(state));
    }

    /**
     * 카카오가 인가 코드를 들고 되돌아오는 착지점. 사용자가 아니라 카카오가 호출한다.
     *
     * <p>모든 파라미터가 {@code required = false}다 — 없으면 Spring이 400을 내고,
     * 그 에러 응답이 브라우저 화면에 그대로 노출된다. 직접 받아 FE로 302시킨다.
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
     * access token 재발급. FE의 앱 부팅·401 인터셉터가 부르는 최다 호출 엔드포인트다.
     *
     * <p>rotation을 하지 않아 응답에 {@code Set-Cookie}가 없다(ADR 0004).
     * <p>쿠키가 없으면 {@code null}이 넘어가 {@code AuthService}가 A007로 거절한다.
     */
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(
            @CookieValue(name = CookieUtil.REFRESH_TOKEN, required = false) String rawRefreshToken) {

        return ApiResponse.success(authService.reissue(rawRefreshToken));
    }

    /**
     * 로그아웃 — 해당 기기의 토큰만 무효화하고 쿠키를 만료시킨다.
     *
     * <p>인증이 필요 없다. access token이 만료된 상태에서도 로그아웃은 되어야 한다.
     * <p>토큰이 이미 없어도 200이다 — {@code AuthService.logout}이 멱등하다.
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
     * <p>{@code UriComponentsBuilder}를 쓰지 않는다 — 그쪽 {@code encode()}는 query 값의
     * {@code :}·{@code /}를 남겨두는데, 카카오는 등록값과 문자열로 대조한다.
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
     * <p>{@code MessageDigest.isEqual}로 상수 시간 비교한다. {@code equals}는 첫 불일치에서
     * 반환해 state를 한 글자씩 맞춰갈 여지를 준다.
     * <p>쿠키 누락이 실무에서 가장 흔한 실패다({@code SameSite}). 불일치와 로그에서 구분한다.
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

    /**
     * FE 복귀 URL에 파라미터 하나를 붙인다.
     *
     * <p>값을 반드시 인코딩한다. 이 자리에는 카카오가 실어 보낸 {@code ?error=} 값이 들어오는데,
     * 콜백은 {@code permitAll}이라 누구나 임의 값을 넣을 수 있다. 인코딩을 빠뜨리면 디코딩된
     * {@code &}로 {@code isNewUser=true}가 주입되거나, 공백이 남아 {@code URI.create}가 터진다.
     * <p>{@code build()}는 인코딩하지 않고 {@code encode()}는 {@code {}}를 변수 자리로 남긴다 —
     * 값을 URI 변수로 넘겨 확장 시점에 인코딩되게 한다.
     */
    private String frontendUri(String name, String value) {
        return UriComponentsBuilder.fromUriString(frontendCompleteUri())
                .queryParam(name, "{value}")
                .encode()
                .buildAndExpand(value)
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
