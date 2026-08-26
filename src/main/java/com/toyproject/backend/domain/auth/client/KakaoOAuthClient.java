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
 * [4단계 · G] 카카오 API 클라이언트
 *
 * <p><b>역할</b><br>
 * 카카오 REST API와 통신하는 <b>유일한 경계</b>. 이 클래스 바깥에서는 카카오의 존재를 모른다.
 *
 * <p><b>에러 변환 — 이 클래스의 핵심 책임</b><br>
 * 카카오 호출 실패는 전부 여기서 {@code BusinessException}으로 변환한다.
 * {@code RestClientException}이 서비스·컨트롤러로 새어 나가면 상위 계층이 HTTP 세부사항을 알게 된다.
 *
 * <table border="1">
 *   <caption>상태별 변환</caption>
 *   <tr><th>상황</th><th>ErrorCode</th><th>판단</th></tr>
 *   <tr><td>4xx</td><td>{@code KAKAO_AUTH_FAILED}(A005, 400)</td><td>우리 요청이 잘못됨</td></tr>
 *   <tr><td>5xx</td><td>{@code KAKAO_API_ERROR}(A006, 502)</td><td>카카오 장애</td></tr>
 *   <tr><td>타임아웃·연결 실패</td><td>{@code KAKAO_API_ERROR}(A006, 502)</td><td>카카오 장애로 취급</td></tr>
 * </table>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>진단을 위해 카카오 <b>응답</b> body는 로그로 남기지만,
 *       요청에 실은 {@code client_secret}·{@code adminKey}는 <b>절대 로그에 넣지 않는다</b>.</li>
 *   <li>{@code unlink}는 여기서 평소처럼 예외를 던진다. best-effort로 삼키는 판단은
 *       {@code AuthService}가 한다(카카오 장애로 탈퇴가 막히면 안 된다).</li>
 * </ul>
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
     * 인가 코드를 카카오 토큰으로 교환한다. <b>콜백 처리의 첫 단계</b>다.
     *
     * <p>{@code POST tokenUri}, {@code application/x-www-form-urlencoded}.
     *
     * <p>⚠️ form의 {@code redirect_uri}는 인가 요청({@code /kakao/authorize}) 때 보낸 값과
     * <b>정확히 같아야</b> 카카오가 대조를 통과시킨다. 양쪽 모두 {@code properties.redirectUri()}를
     * 쓰므로 구조적으로 어긋날 수 없다.
     *
     * <p>인가 코드는 <b>1회용</b>이라 재사용하면 카카오가 4xx로 거부한다(A005).
     * 사용자가 콜백 URL에서 뒤로가기를 눌렀을 때 발생하는 정상적인 실패 경로다.
     *
     * @param code 카카오가 콜백 쿼리로 넘긴 인가 코드
     * @return 카카오 토큰 묶음. 실제로 쓰는 것은 {@code accessToken}뿐이다
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
     * 카카오 사용자 정보를 조회한다. <b>회원 생성·조회의 원천 데이터</b>를 가져온다.
     *
     * <p>{@code GET userInfoUri}, 헤더 {@code Authorization: Bearer {카카오 액세스 토큰}}.
     *
     * <p>응답의 이메일·닉네임은 동의 항목이라 없을 수 있다. 호출부는 반환 DTO의
     * {@code resolveEmail()}·{@code resolveNickname()} 헬퍼로 꺼내 null 처리를 위임한다.
     *
     * @param kakaoAccessToken {@link #requestToken}이 받아온 카카오 액세스 토큰
     * @return 사용자 정보. {@code id}는 항상 있고 나머지는 동의 여부에 따라 없을 수 있다
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
     * 카카오 계정과 우리 서비스의 연결을 끊는다. <b>회원 탈퇴 시 마지막 단계</b>다.
     *
     * <p>{@code POST unlinkUri}, 헤더 {@code Authorization: KakaoAK {adminKey}},
     * form {@code target_id_type=user_id}, {@code target_id={providerId}}.
     *
     * <p>Admin Key로 호출하므로 사용자 토큰이 필요 없다 — <b>탈퇴 시점엔 이미 카카오 토큰이 없다</b>
     * (로그인 직후 버리고 저장하지 않는다).
     *
     * <p>⚠️ 여기서는 실패 시 <b>예외를 던진다.</b> 삼키는 판단은 {@code AuthService.unlinkKakao}가 한다
     * — 카카오 장애로 탈퇴가 막히면 안 되므로 호출부가 best-effort로 다룬다.
     *
     * @param providerId 카카오 고유 사용자 id({@code User.providerId})
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
     * 카카오 호출을 감싸 <b>통신 계층 예외를 A006으로 흡수</b>한다.
     *
     * <p>상태 코드 기반 실패는 {@link #fail}이 {@code BusinessException}으로 이미 변환하므로
     * 여기 걸리지 않고 그대로 전파된다({@code BusinessException}은 {@code RestClientException}이 아니다).
     * 이 catch가 잡는 것은 <b>타임아웃·연결 불가·응답 역직렬화 실패</b>다.
     *
     * @param operation 로그에 남길 작업 이름
     * @param call      실제 호출
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
     * 카카오가 내려준 에러 응답을 로그로 남기고 {@code BusinessException}으로 바꾼다.
     *
     * <p>카카오는 실패 사유를 body의 {@code error}/{@code error_description}(또는 {@code code})으로 주는데,
     * 이걸 남기지 않으면 {@code KOE006}(Redirect URI 미등록) 같은 설정 실수를 추적할 수 없다.
     * 응답 body에는 우리 비밀값이 들어 있지 않다.
     *
     * @throws BusinessException 항상 던진다 (반환하지 않는다)
     */
    private void fail(ClientHttpResponse response, String operation, AuthErrorCode errorCode) throws IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        log.error("카카오 {} 실패 — status={}, body={}", operation, response.getStatusCode(), body);
        throw new BusinessException(errorCode);
    }
}
