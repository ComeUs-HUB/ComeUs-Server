package com.toyproject.backend.domain.auth.token;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * [5단계 · I] 불투명 랜덤 토큰 생성기
 *
 * <p><b>역할</b><br>
 * 추측 불가능한 랜덤 문자열을 만들고, 저장용 해시를 계산한다.
 * {@code refresh_token} 원문과 {@code oauth_state} 값이 모두 여기서 나온다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>refresh token은 <b>JWT가 아니라 불투명 랜덤 문자열</b>이다. DB를 조회해 유효성을 판정하므로
 *       claim을 파싱하는 곳이 없고, 서명·{@code exp}가 전부 무용지물이기 때문이다.</li>
 *   <li>{@code SecureRandom} 32바이트 → base64url 43자. {@code Random}은 예측 가능하므로 쓰지 않는다.</li>
 *   <li>{@code SecureRandom} 인스턴스는 필드로 재사용한다(매번 생성하면 시딩 비용이 든다).
 *       thread-safe하므로 싱글턴 빈의 필드로 안전하다.</li>
 *   <li>반대로 {@code MessageDigest}는 <b>thread-safe가 아니므로 필드로 두지 않고</b>
 *       매 호출 새로 얻는다. 필드로 공유하면 동시 요청에서 해시가 섞인다.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * base64url을 쓰는 이유는 쿠키 값·URL에 그대로 실어도 인코딩이 필요 없기 때문이다.
 * 일반 base64({@code +}, {@code /}, {@code =})를 쓰면 쿠키 파싱에서 문제가 된다.
 *
 * <p><b>⚠️ 결정 필요 — {@code oauth_state} 생성 주체</b><br>
 * {@code docs/SEQUENCE/phase2-auth.md}의 I 항목은 refresh token 생성만 다루고,
 * state는 8단계 서술에서 "{@code SecureRandom}으로 생성"이라고만 한다.
 * 이 클래스의 {@link #generate()}가 <b>둘 다 담당</b>하는 것으로 두었으니,
 * 7~8단계에서 {@code AuthService.generateOAuthState()}가 자체 {@code SecureRandom}을
 * 새로 만들지 않도록 이 메서드를 재사용할 것. (state는 쿠키 값과 대조만 하면 되므로 43자로 충분하다.)
 */
@Component
public class SecureTokenGenerator {

    /** 32바이트 = 256비트. base64url로 정확히 43자(패딩 제외)가 된다. */
    private static final int TOKEN_BYTE_LENGTH = 32;

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 추측 불가능한 불투명 토큰 원문을 만든다. {@code refresh_token}·{@code oauth_state} 공용이다.
     *
     * <p>이 값이 <b>원문</b>이다. refresh token으로 쓸 때 DB에는 이 값이 아니라
     * {@link #sha256Hex(String)} 결과만 저장하고, 원문은 {@code Set-Cookie}로만 내보낸다.
     *
     * @return base64url 43자 ({@code A-Z a-z 0-9 - _}만 포함, 패딩 없음)
     */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /**
     * 토큰 원문의 저장용 해시를 계산한다.
     *
     * <p>SHA-256 hex는 <b>항상 정확히 64자</b>라 {@code refresh_tokens.token_hash}의
     * {@code CHAR(64)}와 길이가 그대로 맞는다. {@code HexFormat.of()}는 소문자를 내므로
     * 대소문자 정규화가 따로 필요 없다 — 조회 키로 쓰이는 값이라 표현이 흔들리면 안 된다.
     *
     * <p>salt를 쓰지 않는 이유: 입력이 이미 256비트 고엔트로피 랜덤이라 사전 공격 대상이 아니고,
     * 무엇보다 <b>해시로 조회</b>해야 하므로 row마다 다른 salt를 쓸 수 없다.
     *
     * @param raw 토큰 원문
     * @return 소문자 hex 64자
     */
    public String sha256Hex(String raw) {
        return HexFormat.of().formatHex(digest().digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * SHA-256 {@code MessageDigest}를 새로 얻는다.
     *
     * <p>SHA-256은 모든 JDK 구현이 제공해야 하는 필수 알고리즘이라
     * {@code NoSuchAlgorithmException}은 <b>도달 불가능한 경로</b>다.
     * 사용자 입력과 무관한 런타임 환경 문제이므로 {@code BusinessException}(=클라이언트에게
     * 코드로 설명되는 실패)이 아니라 {@code IllegalStateException}으로 감싼다.
     */
    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 지원하지 않는 JVM입니다.", e);
        }
    }
}
