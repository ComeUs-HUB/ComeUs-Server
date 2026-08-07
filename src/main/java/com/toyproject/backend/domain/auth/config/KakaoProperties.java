package com.toyproject.backend.domain.auth.config;

/**
 * [3단계 · G] 카카오 연동 설정 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code kakao.*} 설정을 타입 안전하게 묶는다. 고정 엔드포인트는 {@code application.yml}에,
 * 비밀값·환경별 주소는 프로파일별 yml(gitignore 대상)에 둔다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code @ConfigurationProperties(prefix = "kakao")}를 붙인 <b>record</b>로 만든다(생성자 바인딩).</li>
 *   <li>{@code BackendApplication}에 {@code @ConfigurationPropertiesScan}이 필요하다.</li>
 * </ul>
 *
 * <p><b>예정 컴포넌트</b>
 * <ul>
 *   <li>{@code clientId} / {@code clientSecret} / {@code adminKey} — 프로파일별 yml, 환경변수 주입</li>
 *   <li>{@code redirectUri} — 프로파일별 yml</li>
 *   <li>{@code authorizeUri} / {@code tokenUri} / {@code userInfoUri} / {@code unlinkUri} — {@code application.yml} 고정</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * {@code redirectUri}는 <b>BE 콜백 주소</b>({@code {api-host}/api/auth/kakao/callback})다. FE 주소가 아니다.
 * OAuth Authorization Code Grant는 {@code redirect_uri}를 confidential client(백엔드)가 소유하는 것을 전제한다.
 * 카카오 개발자 콘솔에 <b>동일한 값</b>이 등록돼 있어야 하며, 누락 시 카카오가 {@code KOE006}으로 거부해
 * 첫 시도부터 실패한다. local/prod 양쪽 모두 등록할 것.
 *
 * <p>엔드포인트: 인가 {@code GET https://kauth.kakao.com/oauth/authorize} ·
 * 토큰 {@code POST https://kauth.kakao.com/oauth/token} ·
 * 사용자정보 {@code GET https://kapi.kakao.com/v2/user/me} ·
 * 연결해제 {@code POST https://kapi.kakao.com/v1/user/unlink}(Admin Key)
 */
public record KakaoProperties(/* 구현 단계에서 정의 */) {
}
