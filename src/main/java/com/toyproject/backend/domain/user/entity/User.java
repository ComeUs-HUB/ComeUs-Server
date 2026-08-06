package com.toyproject.backend.domain.user.entity;

/**
 * [1단계 · G] 회원 엔티티 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * 카카오 OAuth로 가입한 회원의 상태를 보관한다. 이후 모든 도메인(Cart·Order·Review)이
 * 이 엔티티를 소유자로 참조하므로 Phase 2에서 가장 먼저 확정되어야 한다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>카카오 전용이므로 {@code password} 컬럼을 두지 않는다. LOCAL 로그인은 향후 과제.</li>
 *   <li>Setter를 만들지 않는다. 상태 변경은 도메인 메서드로만 열어 둔다.</li>
 *   <li>탈퇴는 소프트 삭제 — row를 지우지 않고 {@code status}만 전환한다.</li>
 *   <li>{@code @EntityListeners(AuditingEntityListener.class)}로 생성/수정 시각을 자동 관리한다.</li>
 * </ul>
 *
 * <p><b>예정 필드</b>
 * <ul>
 *   <li>{@code id} — PK, IDENTITY</li>
 *   <li>{@code email} — nullable + unique. 카카오 이메일 동의를 거부하면 null</li>
 *   <li>{@code name} — nullable. 카카오 닉네임으로 초기화</li>
 *   <li>{@code phone} / {@code address} / {@code birthDate} — 전부 nullable. {@code PUT /users/me}로 추후 입력(강제 아님)</li>
 *   <li>{@code role} — {@link Role}, not null, 기본 USER</li>
 *   <li>{@code provider} — {@link Provider}, not null</li>
 *   <li>{@code providerId} — 카카오 고유 사용자 id, not null</li>
 *   <li>{@code status} — {@link UserStatus}, not null, 기본 ACTIVE</li>
 *   <li>{@code withdrawnAt} — nullable</li>
 *   <li>{@code createdAt} / {@code updatedAt} — {@code @CreatedDate} / {@code @LastModifiedDate}</li>
 * </ul>
 *
 * <p><b>예정 도메인 메서드</b>
 * <ul>
 *   <li>{@code ofKakao(providerId, email, name)} — 정적 팩터리. role=USER, status=ACTIVE로 초기화</li>
 *   <li>{@code updateProfile(name, phone, address, birthDate)} — null인 필드는 변경하지 않는 부분 수정</li>
 *   <li>{@code withdraw()} — status=WITHDRAWN + withdrawnAt 기록</li>
 *   <li>{@code reactivate()} — 탈퇴 회원 재로그인 시 자동 재활성화</li>
 *   <li>{@code isWithdrawn()}</li>
 * </ul>
 *
 * <p><b>제약</b>
 * <ul>
 *   <li>{@code email} 단일 유니크</li>
 *   <li>{@code (provider, providerId)} 복합 유니크 — 동일 카카오 계정 중복 가입 방지</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * enum 필드에 {@code @Enumerated(STRING)}만 붙이면 MySQL 방언이 네이티브 {@code enum(...)} 컬럼을
 * 생성해 마이그레이션의 {@code VARCHAR(20)}과 어긋난다({@code ddl-auto: validate} 실패 위험).
 * {@code @JdbcTypeCode(SqlTypes.VARCHAR)}로 고정할 것.
 */
public class User {
}
