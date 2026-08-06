package com.toyproject.backend.domain.user.entity;

/**
 * [1단계 · G] 리프레시 토큰 엔티티 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * 발급된 refresh token의 <b>해시</b>와 만료 시각을 보관한다. 재발급 요청이 오면 이 테이블을 조회해
 * 유효성을 판정하므로, 토큰 자체는 JWT가 아니어도 된다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li><b>user 1 : token N</b> — 로그아웃이 해당 기기만 무효화하므로 멀티 디바이스 동시 로그인이 전제다.
 *       {@code @OneToOne}으로 만들면 다른 기기가 강제 로그아웃된다. 반드시 {@code @ManyToOne(LAZY)}.</li>
 *   <li><b>DB엔 해시만 저장</b> — 원문은 {@code Set-Cookie}로만 나간다. 서버는 요청받은 원문을 SHA-256으로
 *       해시해 조회하므로 원문을 보관할 이유가 없다(DB 유출 시 토큰이 그대로 노출되는 것을 막는다).</li>
 *   <li>Rotation을 하지 않으므로 재발급 시 이 row는 건드리지 않는다.</li>
 * </ul>
 *
 * <p><b>예정 필드</b>
 * <ul>
 *   <li>{@code id} — PK</li>
 *   <li>{@code user} — {@code @ManyToOne(fetch = LAZY)} + {@code @JoinColumn(name = "user_id", nullable = false)}</li>
 *   <li>{@code tokenHash} — {@code CHAR(64)}, 원문의 SHA-256 hex, unique</li>
 *   <li>{@code expiresAt} — 만료 판정의 <b>유일한</b> 근거</li>
 *   <li>{@code createdAt} — {@code @CreatedDate}</li>
 * </ul>
 *
 * <p><b>예정 도메인 메서드</b>
 * <ul>
 *   <li>{@code issue(user, tokenHash, expiresAt)} — 정적 팩터리</li>
 *   <li>{@code isExpired()} — {@code expiresAt}가 현재보다 과거인지</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * 유효성 = <b>row 존재 AND {@code expiresAt > now()}</b>. row 존재만 검사하면 만료된 토큰이
 * 영구히 유효해진다. 만료 row 정리 배치는 후속 과제지만, {@code expiresAt} 검사가 있으므로 기능상 무해하다.
 *
 * <p>수명 관리: 로그아웃은 해당 row만 삭제 / 탈퇴는 해당 유저의 전체 row 삭제.
 */
public class RefreshToken {
}
