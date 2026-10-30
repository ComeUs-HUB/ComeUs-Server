package com.toyproject.backend.domain.auth.token;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * [Phase 2 · 5단계] 추측 불가능한 랜덤 문자열과 저장용 해시를 만든다.
 * {@code refresh_token} 원문과 {@code oauth_state}가 모두 여기서 나온다.
 *
 * <p>불투명 랜덤을 쓰는 이유와 해시만 저장하는 이유는 ADR 0005.
 * <p>base64url이라 쿠키 값·URL에 그대로 실을 수 있다. 일반 base64는 쿠키 파싱에서 깨진다.
 */
@Component
public class SecureTokenGenerator {

    /** 32바이트 = 256비트. base64url로 정확히 43자(패딩 제외)가 된다. */
    private static final int TOKEN_BYTE_LENGTH = 32;

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom secureRandom = new SecureRandom();

    /** 이 값이 원문이다. refresh token은 {@link #sha256Hex}만 저장하고 원문은 쿠키로만 나간다. */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /**
     * 저장용 해시. {@code HexFormat.of()}가 소문자를 내므로 정규화가 따로 필요 없다.
     *
     * <p>salt를 쓰지 않는다 — 해시로 조회해야 해서 row마다 다른 salt를 쓸 수 없고,
     * 입력이 이미 256비트 고엔트로피 랜덤이라 사전 공격 대상이 아니다.
     */
    public String sha256Hex(String raw) {
        return HexFormat.of().formatHex(digest().digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 매 호출 새로 얻는다 — {@code MessageDigest}는 thread-safe가 아니라 필드로 공유하면
     * 동시 요청에서 해시가 섞인다.
     *
     * <p>SHA-256은 모든 JDK가 제공해야 하므로 {@code NoSuchAlgorithmException}은 도달 불가다.
     * 사용자 입력과 무관한 환경 문제라 {@code BusinessException}으로 감싸지 않는다.
     */
    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 지원하지 않는 JVM입니다.", e);
        }
    }
}
