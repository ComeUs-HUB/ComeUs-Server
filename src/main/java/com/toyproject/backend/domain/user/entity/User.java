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
 * [1단계 · G] 회원 엔티티
 *
 * <p><b>역할</b><br>
 * 카카오 OAuth로 가입한 회원의 상태를 보관한다. 이후 모든 도메인(Cart·Order·Review)이
 * 이 엔티티를 소유자로 참조한다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>카카오 전용이므로 {@code password} 컬럼을 두지 않는다. LOCAL 로그인은 향후 과제.</li>
 *   <li>Setter를 만들지 않는다. 상태 변경은 아래 도메인 메서드로만 열려 있다.</li>
 *   <li>탈퇴는 소프트 삭제 — row를 지우지 않고 {@code status}만 전환한다.</li>
 *   <li>{@code email}은 nullable + unique. 카카오 이메일 동의를 거부하거나 이미 쓰인 주소면 null로 둔다.
 *       MySQL·H2 모두 unique 컬럼에 NULL 중복을 허용하므로 이 조합이 성립한다.</li>
 * </ul>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>테이블명이 {@code users}인 이유: {@code USER}는 표준 SQL 및 PostgreSQL 등에서 예약어라
 *       DB를 옮길 때 인용부호가 필요해진다(MySQL에서는 비예약 키워드라 당장은 무해하다).</li>
 *   <li>enum 필드에 {@code @Enumerated(STRING)}만 붙이면 MySQL 방언이 네이티브 {@code enum(...)} 컬럼을
 *       생성해 마이그레이션의 {@code VARCHAR(20)}과 어긋난다({@code ddl-auto: validate} 실패).
 *       {@code @JdbcTypeCode(SqlTypes.VARCHAR)}로 고정한다.</li>
 *   <li>유니크 제약을 {@code V1__init.sql}에만 두면 {@code @DataJpaTest}(H2, 엔티티 매핑으로 스키마 생성)에서
 *       재현되지 않아 중복 가입 테스트가 통과해 버린다. 그래서 {@code @Table}에도 선언한다.</li>
 * </ul>
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
     * @param email 카카오 동의 거부 또는 이미 사용 중인 주소면 {@code null}
     * @param name  카카오 닉네임. 미동의 시 {@code null}
     */
    public static User ofKakao(String providerId, String email, String name) {
        return new User(providerId, email, name);
    }

    /**
     * 부분 수정. <b>{@code null}인 인자는 "변경하지 않음"</b>을 뜻하므로,
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
     * [Phase 3 · 1단계] ADMIN 승격.
     *
     * <p><b>이 메서드를 호출하는 API는 없다.</b> ADMIN 발급은 운영자가 DB에서 직접 한다
     * ({@code UPDATE users SET role = 'ADMIN' WHERE id = ?}). 그런데도 메서드를 두는 이유는
     * 이 엔티티가 <b>Setter를 만들지 않는다</b>는 규칙 때문이다 — {@code role}을 바꾸는 경로가
     * 코드에 필요해지는 순간(테스트가 ADMIN 회원을 만들 때) 그 경로도 이름이 붙은 도메인 메서드여야 한다.
     * 승격 API가 생기면 그 API가 이 메서드를 호출한다.
     *
     * <p><b>승격은 재로그인 없이 즉시 반영된다.</b> {@code role}은 access token의 claim이 아니라
     * {@code CustomUserDetailsService}가 매 요청 DB에서 다시 읽는 값이다. 이미 발급된 토큰을
     * 그대로 쓰면서 권한만 올라간다 — claim을 신뢰하는 구조였다면 토큰 만료(30분)를 기다려야 했다.
     */
    public void promoteToAdmin() {
        this.role = Role.ADMIN;
    }

    public boolean isWithdrawn() {
        return this.status == UserStatus.WITHDRAWN;
    }
}
