package com.toyproject.backend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * [Phase 2 · 1단계] 카카오 OAuth로 가입한 회원. 이후 모든 도메인이 소유자로 참조한다.
 *
 * <p>Setter를 두지 않는다 — 상태 변경은 아래 도메인 메서드로만 열려 있다.
 * <p>탈퇴는 소프트 삭제다. {@code email}은 nullable + unique이고 동의 거부·중복이면 비운다.
 * <p>enum은 {@code @JdbcTypeCode(VARCHAR)}로 고정한다 — MySQL 방언이 네이티브 {@code enum}
 * 컬럼을 만들면 마이그레이션의 {@code VARCHAR(20)}과 어긋나 {@code validate}가 깨진다.
 * <p>유니크 제약을 {@code @Table}에도 선언한다. V1에만 두면 H2에서 재현되지 않는다.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_provider_provider_id", columnNames = {"provider", "provider_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Column(name = "provider_id", nullable = false, length = 64)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    private LocalDateTime withdrawnAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private User(String providerId, String email, String name) {
        this.provider = Provider.KAKAO;
        this.providerId = providerId;
        this.email = email;
        this.name = name;
        this.role = Role.USER;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * 카카오 신규 가입. {@code role=USER}, {@code status=ACTIVE}로 초기화한다.
     *
     * @param email 동의 거부 또는 이미 사용 중인 주소면 {@code null}
     * @param name  카카오 닉네임. 미동의 시 {@code null}
     */
    public static User ofKakao(String providerId, String email, String name) {
        return new User(providerId, email, name);
    }

    /**
     * 부분 수정. {@code null}인 인자는 "변경하지 않음"을 뜻하므로,
     * 이 메서드로는 이미 채워진 값을 다시 비울 수 없다.
     */
    public void updateProfile(String name, String phone, String address, LocalDate birthDate) {
        if (name != null) {
            this.name = name;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (address != null) {
            this.address = address;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
    }

    /** 소프트 삭제. refresh token 삭제와 카카오 unlink는 auth 도메인의 몫이다. */
    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
    }

    /** 탈퇴 회원이 동일 카카오 계정으로 재로그인했을 때의 자동 재활성화. */
    public void reactivate() {
        this.status = UserStatus.ACTIVE;
        this.withdrawnAt = null;
    }

    /**
     * [Phase 3 · 1단계] ADMIN 승격. 호출하는 API는 없고 운영자가 DB에서 직접 바꾼다.
     *
     * <p>그래도 메서드를 두는 것은 Setter를 만들지 않기 때문이다 — {@code role}을 바꾸는 경로가
     * 필요해지면(테스트가 ADMIN을 만들 때) 그 경로도 이름이 붙은 도메인 메서드여야 한다.
     * <p>재로그인 없이 즉시 반영된다. {@code role}은 claim이 아니라 매 요청 DB에서 읽는다.
     */
    public void promoteToAdmin() {
        this.role = Role.ADMIN;
    }

    public boolean isWithdrawn() {
        return this.status == UserStatus.WITHDRAWN;
    }
}
