package com.toyproject.backend.domain.auth.token;

/**
 * [5단계 · I] 불투명 랜덤 토큰 생성기 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * 추측 불가능한 랜덤 문자열을 만들고, 저장용 해시를 계산한다.
 * {@code refresh_token} 원문과 {@code oauth_state} 값이 모두 여기서 나온다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>refresh token은 <b>JWT가 아니라 불투명 랜덤 문자열</b>이다. DB를 조회해 유효성을 판정하므로
 *       claim을 파싱하는 곳이 없고, 서명·{@code exp}가 전부 무용지물이기 때문이다.</li>
 *   <li>{@code SecureRandom} 32바이트 → base64url 43자. {@code Random}은 예측 가능하므로 쓰지 않는다.</li>
 *   <li>{@code SecureRandom} 인스턴스는 필드로 재사용한다(매번 생성하면 시딩 비용이 든다).</li>
 * </ul>
 *
 * <p><b>예정 메서드</b>
 * <ul>
 *   <li>{@code String generate()} — 32바이트 랜덤 → base64url(패딩 없음) 43자</li>
 *   <li>{@code String sha256Hex(String raw)} — 소문자 hex 64자.
 *       {@code refresh_tokens.token_hash}의 {@code CHAR(64)}와 길이가 정확히 맞는다.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * base64url을 쓰는 이유는 쿠키 값·URL에 그대로 실어도 인코딩이 필요 없기 때문이다.
 * 일반 base64({@code +}, {@code /}, {@code =})를 쓰면 쿠키 파싱에서 문제가 된다.
 */
public class SecureTokenGenerator {
}
