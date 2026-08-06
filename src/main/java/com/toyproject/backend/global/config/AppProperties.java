package com.toyproject.backend.global.config;

/**
 * [6단계 · H] 앱 환경 설정 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * FE 복귀 주소와 CORS 허용 origin을 묶는다. 배포 환경마다 달라지는 값이라 코드에 박지 않는다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code @ConfigurationProperties(prefix = "app")} record. 중첩 record로 그룹을 나눈다.</li>
 *   <li>프로파일별 yml(gitignore 대상)에서 주입한다. local은 {@code http://localhost:3000}.</li>
 * </ul>
 *
 * <p><b>예정 구조</b>
 * <ul>
 *   <li>{@code frontend.baseUrl} — 로그인 완료 후 302로 복귀할 FE 주소.
 *       {@code AuthController}가 {@code {baseUrl}/oauth/complete}를 조립한다.</li>
 *   <li>{@code cors.allowedOrigins} — 허용 origin <b>명시 목록</b></li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * 쿠키 왕복 때문에 {@code allowCredentials(true)}가 필수인데, 그러면 CORS 명세상
 * origin 와일드카드({@code *})를 쓸 수 없다. 반드시 명시 목록이어야 한다.
 */
public record AppProperties(/* 구현 단계에서 정의 */) {
}
