package com.toyproject.backend.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * [4단계 · G] 카카오 연동 설정
 *
 * <p><b>역할</b><br>
 * {@code kakao.*} 설정을 타입 안전하게 묶는다. 고정 엔드포인트는 {@code application.yml}에,
 * 비밀값·환경별 주소는 프로파일별 yml(gitignore 대상)에 둔다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>record이므로 생성자 바인딩으로 동작한다.
 *       {@code BackendApplication}의 {@code @ConfigurationPropertiesScan}이 이 클래스를 찾아 등록한다.</li>
 *   <li><b>{@code @Validated}/{@code @NotBlank}를 붙이지 않는다.</b> 테스트 yml
 *       ({@code src/test/resources/application.yml})이 main {@code application.yml}을 shadow해
 *       테스트 컨텍스트에는 {@code kakao.*}가 존재하지 않는다. 검증을 걸면 {@code contextLoads}가 깨진다.
 *       검증 없이는 누락된 값이 {@code null}로 바인딩되고 넘어간다.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * {@code redirectUri}는 <b>BE 콜백 주소</b>({@code {api-host}/api/auth/kakao/callback})다. FE 주소가 아니다.
 * OAuth Authorization Code Grant는 {@code redirect_uri}를 confidential client(백엔드)가 소유하는 것을 전제한다.
 * 카카오 개발자 콘솔에 <b>동일한 값</b>이 등록돼 있어야 하며, 누락 시 카카오가 {@code KOE006}으로 거부해
 * 첫 시도부터 실패한다. local/prod 양쪽 모두 등록할 것.
 *
 * @param clientId     REST API 키. 인가 요청·토큰 교환에 쓴다
 * @param clientSecret 토큰 교환 시 클라이언트 인증에 쓴다. <b>로그에 남기지 않는다</b>
 * @param adminKey     Admin Key. 연결 해제({@code unlink})에만 쓴다. <b>로그에 남기지 않는다</b>
 * @param redirectUri  BE 콜백 주소. 인가 요청과 토큰 교환에 <b>같은 값</b>을 보내야 카카오가 대조를 통과시킨다
 * @param authorizeUri 인가 화면 주소. {@code AuthController}가 302 대상 URL을 조립할 때 쓴다(8단계)
 * @param tokenUri     인가 코드 → 카카오 토큰 교환
 * @param userInfoUri  카카오 사용자 정보 조회
 * @param unlinkUri    카카오 연결 해제 (탈퇴 시)
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(

        String clientId,
        String clientSecret,
        String adminKey,
        String redirectUri,
        String authorizeUri,
        String tokenUri,
        String userInfoUri,
        String unlinkUri
) {
}
