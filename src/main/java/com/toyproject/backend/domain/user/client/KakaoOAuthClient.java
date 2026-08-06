package com.toyproject.backend.domain.user.client;

/**
 * [3단계 · G] 카카오 API 클라이언트 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * 카카오 REST API와 통신하는 <b>유일한 경계</b>. 이 클래스 바깥에서는 카카오의 존재를 모른다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li><b>카카오 호출 실패는 전부 이 경계에서 {@code BusinessException}으로 변환</b>한다.
 *       {@code RestClientException}이 서비스·컨트롤러로 새어 나가면 상위 계층이 HTTP 세부사항을 알게 된다.</li>
 *   <li>4xx는 우리 요청이 잘못된 것 → {@code KAKAO_AUTH_FAILED}(U007, 400)<br>
 *       5xx·통신 오류는 카카오 장애 → {@code KAKAO_API_ERROR}(U008, 502)</li>
 *   <li>{@code @Qualifier}로 kauth/kapi 클라이언트를 구분해 주입받는다.</li>
 * </ul>
 *
 * <p><b>예정 메서드</b>
 * <ul>
 *   <li>{@code KakaoTokenResponse requestToken(String code)}<br>
 *       — {@code POST tokenUri}, {@code application/x-www-form-urlencoded}.<br>
 *       form: {@code grant_type=authorization_code}, {@code client_id}, {@code client_secret},
 *       {@code redirect_uri}(설정값), {@code code}.<br>
 *       {@code redirect_uri}는 authorize 때 보낸 값과 <b>정확히 같아야</b> 카카오가 대조를 통과시킨다.</li>
 *   <li>{@code KakaoUserInfoResponse fetchUserInfo(String kakaoAccessToken)}<br>
 *       — {@code GET userInfoUri}, 헤더 {@code Authorization: Bearer {카카오 액세스 토큰}}</li>
 *   <li>{@code void unlink(String providerId)}<br>
 *       — {@code POST unlinkUri}, 헤더 {@code Authorization: KakaoAK {adminKey}},
 *       form: {@code target_id_type=user_id}, {@code target_id}.<br>
 *       Admin Key로 호출하므로 사용자 토큰이 필요 없다(탈퇴 시점엔 이미 없다).</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * {@code unlink}는 호출부에서 <b>best-effort</b>로 다뤄진다. 여기서는 평소처럼 예외를 던지고,
 * 삼키는 판단은 {@code UserService}가 한다(카카오 장애로 탈퇴가 막히면 안 된다).
 *
 * <p><b>검증</b>: {@code MockRestServiceServer}로 4xx/5xx 응답이 각각 U007/U008로 변환되는지 확인한다.
 */
public class KakaoOAuthClient {
}
