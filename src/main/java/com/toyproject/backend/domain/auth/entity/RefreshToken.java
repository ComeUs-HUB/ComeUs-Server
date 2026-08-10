package com.toyproject.backend.domain.auth.entity;

import com.toyproject.backend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * [1단계 · G] 리프레시 토큰 엔티티
 *
 * <p><b>역할</b><br>
 * 발급된 refresh token의 <b>해시</b>와 만료 시각을 보관한다. 재발급 요청이 오면 이 테이블을 조회해
 * 유효성을 판정하므로, 토큰 자체는 JWT가 아니어도 된다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li><b>user 1 : token N</b> — 로그아웃이 해당 기기만 무효화하므로 멀티 디바이스 동시 로그인이 전제다.
 *       {@code @OneToOne}으로 만들면 다른 기기가 강제 로그아웃된다.</li>
 *   <li><b>DB엔 해시만 저장</b> — 원문은 {@code Set-Cookie}로만 나간다. 서버는 요청받은 원문을 SHA-256으로
 *       해시해 조회하므로 원문을 보관할 이유가 없다(DB 유출 시 토큰이 그대로 노출되는 것을 막는다).</li>
 *   <li>Rotation을 하지 않으므로 재발급 시 이 row는 건드리지 않는다.</li>
 *   <li>{@code updatedAt}이 없다 — 발급 후 변경되지 않고 삭제만 되기 때문이다.</li>
 * </ul>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>유효성 = <b>row 존재 AND {@code expiresAt > now()}</b>. row 존재만 검사하면 만료된 토큰이
 *       영구히 유효해진다. 만료 row 정리 배치는 후속 과제지만, {@code expiresAt} 검사가 있으므로 기능상 무해하다.</li>
 *   <li>{@code tokenHash}가 {@code char(64)}인 이유: SHA-256 hex는 <b>항상</b> 정확히 64자다.
 *       {@code columnDefinition}으로 못박아 {@code V1__init.sql}과 타입이 어긋나지 않게 한다.</li>
 * </ul>
 *
 * <p>수명 관리: 로그아웃은 해당 row만 삭제 / 탈퇴는 해당 유저의 전체 row 삭제.
 */
@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_refresh_tokens_token_hash", columnNames = "token_hash"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private RefreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * @param tokenHash 토큰 <b>원문이 아니라</b> SHA-256 hex (소문자 64자)
     */
    public static RefreshToken issue(User user, String tokenHash, LocalDateTime expiresAt) {
        return new RefreshToken(user, tokenHash, expiresAt);
    }

    public boolean isExpired() {
        return this.expiresAt.isBefore(LocalDateTime.now());
    }
}
