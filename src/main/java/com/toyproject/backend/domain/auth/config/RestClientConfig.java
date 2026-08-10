package com.toyproject.backend.domain.auth.config;

/**
 * [4단계 · G] 카카오 호출용 RestClient 설정 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code KakaoOAuthClient}가 쓸 HTTP 클라이언트 Bean을 제공한다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li><b>신규 의존성이 필요 없다.</b> {@code RestClient}는 {@code spring-boot-starter-web}에 포함돼 있다.
 *       {@code starter-oauth2-client}·webflux는 도입하지 않는다(카카오 연동만을 위해 인증 스택 전체를
 *       끌어오면 우리가 확정한 쿠키·리다이렉트 설계와 충돌한다).</li>
 *   <li>{@code kauth}(토큰 교환)용과 {@code kapi}(사용자정보·연결해제)용 <b>Bean 2개</b>로 나눈다.</li>
 *   <li>URI는 {@code KakaoProperties}의 절대 주소를 그대로 쓰므로 baseUrl을 설정하지 않는다.</li>
 *   <li>타임아웃을 반드시 지정한다. 미설정 시 카카오 지연이 그대로 우리 스레드 점유로 이어진다.</li>
 * </ul>
 *
 * <p><b>예정 Bean</b>
 * <ul>
 *   <li>{@code RestClient kauthRestClient(RestClient.Builder)}</li>
 *   <li>{@code RestClient kapiRestClient(RestClient.Builder)}</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * 같은 타입 Bean이 2개라 주입 지점에서 {@code @Qualifier}가 필요하다.
 * 요청 팩터리는 Boot 버전별로 API가 바뀌므로({@code ClientHttpRequestFactories}는 폐기 예정)
 * spring-web의 {@code SimpleClientHttpRequestFactory}를 직접 쓰는 편이 안정적이다.
 */
public class RestClientConfig {
}
