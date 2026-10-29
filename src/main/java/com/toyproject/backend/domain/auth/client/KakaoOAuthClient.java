package com.toyproject.backend.domain.auth.client;

import com.toyproject.backend.domain.auth.client.dto.KakaoTokenResponse;
import com.toyproject.backend.domain.auth.client.dto.KakaoUserInfoResponse;
import com.toyproject.backend.domain.auth.config.KakaoProperties;
import com.toyproject.backend.domain.auth.exception.AuthErrorCode;
import com.toyproject.backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * [Phase 2 · 4단계] 카카오 REST API와 통신하는 유일한 경계.
 * 바깥 계층은 카카오의 존재를 모른다.
 *
 * <p>호출 실패를 전부 {@code BusinessException}으로 바꾸는 것이 이 클래스의 핵심 책임이다.
 * 4xx는 A005(우리 요청이 잘못됨), 5xx·통신 실패는 A006(카카오 장애)으로 간다.
 * <p>응답 body는 진단을 위해 로그로 남기되 {@code clientSecret}·{@code adminKey}는 넣지 않는다.
 */
@Slf4j
@Component
public class KakaoOAuthClient {

    private final RestClient kauthRestClient;
    private final RestClient kapiRestClient;
    private final KakaoProperties properties;

    public KakaoOAuthClient(@Qualifier("kauthRestClient") RestClient kauthRestClient,
                            @Qualifier("kapiRestClient") RestClient kapiRestClient,
                            KakaoProperties properties) {
        this.kauthRestClient = kauthRestClient;
        this.kapiRestClient = kapiRestClient;
        this.properties = properties;
    }

    /**
     * 인가 코드를 카카오 토큰으로 교환한다. 콜백 처리의 첫 단계다.
     *
     * <p>form의 {@code redirect_uri}는 인가 요청 때 보낸 값과 정확히 같아야 한다 — 양쪽 모두
     * {@code properties.redirectUri()}를 쓰므로 구조적으로 어긋날 수 없다(ADR 0002).
     * <p>인가 코드는 1회용이라 재사용하면 A005다. 콜백에서 뒤로가기를 누르면 생기는 정상 경로다.
     *
     * @throws BusinessException 4xx면 A005, 5xx·통신 실패면 A006
     */
    public KakaoTokenResponse requestToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);

        return execute("토큰 교환", () -> kauthRestClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        (request, response) -> fail(response, "토큰 교환", AuthErrorCode.KAKAO_AUTH_FAILED))
                .onStatus(HttpStatusCode::is5xxServerError,
                        (request, response) -> fail(response, "토큰 교환", AuthErrorCode.KAKAO_API_ERROR))
                .body(KakaoTokenResponse.class));
    }

    /**
     * 카카오 사용자 정보를 조회한다. 회원 생성·조회의 원천 데이터다.
     *
     * <p>이메일·닉네임은 동의 항목이라 없을 수 있다. 반환 DTO의 헬퍼가 null을 흡수한다.
     *
     * @throws BusinessException 4xx(토큰 만료·무효)면 A005, 5xx·통신 실패면 A006
     */
    public KakaoUserInfoResponse fetchUserInfo(String kakaoAccessToken) {
        return execute("사용자 정보 조회", () -> kapiRestClient.get()
                .uri(properties.userInfoUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        (request, response) -> fail(response, "사용자 정보 조회", AuthErrorCode.KAKAO_AUTH_FAILED))
                .onStatus(HttpStatusCode::is5xxServerError,
                        (request, response) -> fail(response, "사용자 정보 조회", AuthErrorCode.KAKAO_API_ERROR))
                .body(KakaoUserInfoResponse.class));
    }

    /**
     * 카카오 계정과의 연결을 끊는다. 탈퇴의 마지막 단계다.
     *
     * <p>Admin Key로 호출해 사용자 토큰이 필요 없다 — 탈퇴 시점엔 이미 카카오 토큰이 없다.
     * <p>실패 시 그대로 던진다. best-effort로 삼키는 판단은 {@code AuthService}가 한다.
     *
     * @throws BusinessException 4xx면 A005, 5xx·통신 실패면 A006
     */
    public void unlink(String providerId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", providerId);

        execute("연결 해제", () -> kapiRestClient.post()
                .uri(properties.unlinkUri())
                .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.adminKey())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        (request, response) -> fail(response, "연결 해제", AuthErrorCode.KAKAO_AUTH_FAILED))
                .onStatus(HttpStatusCode::is5xxServerError,
                        (request, response) -> fail(response, "연결 해제", AuthErrorCode.KAKAO_API_ERROR))
                .toBodilessEntity());
    }

    /**
     * 통신 계층 예외를 A006으로 흡수한다.
     *
     * <p>상태 코드 기반 실패는 {@link #fail}이 이미 바꿔 놓아 여기 걸리지 않는다
     * ({@code BusinessException}은 {@code RestClientException}이 아니다).
     * 이 catch가 잡는 것은 타임아웃·연결 불가·역직렬화 실패다.
     */
    private <T> T execute(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException e) {
            log.error("카카오 {} 통신 실패", operation, e);
            throw new BusinessException(AuthErrorCode.KAKAO_API_ERROR);
        }
    }

    /**
     * 에러 응답을 로그로 남기고 {@code BusinessException}으로 바꾼다.
     *
     * <p>카카오는 실패 사유를 body로 준다 — 남기지 않으면 {@code KOE006}(Redirect URI 미등록)
     * 같은 설정 실수를 추적할 수 없다. 응답 body에는 우리 비밀값이 없다.
     *
     * @throws BusinessException 항상 던진다
     */
    private void fail(ClientHttpResponse response, String operation, AuthErrorCode errorCode) throws IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        log.error("카카오 {} 실패 — status={}, body={}", operation, response.getStatusCode(), body);
        throw new BusinessException(errorCode);
    }
}
