package com.toyproject.backend.domain.auth.repository;

/**
 * [3단계 · G] 리프레시 토큰 Repository — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * 재발급 시 토큰 조회, 로그아웃/탈퇴 시 토큰 무효화를 담당한다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code JpaRepository<RefreshToken, Long>}를 확장한다.</li>
 *   <li>삭제는 엔티티를 로딩한 뒤 지우지 않고 <b>벌크 삭제</b>({@code @Modifying @Query})로 처리한다.
 *       무효화가 목적이라 엔티티 상태가 필요 없다.</li>
 * </ul>
 *
 * <p><b>예정 메서드</b>
 * <ul>
 *   <li>{@code Optional<RefreshToken> findByTokenHash(String)}<br>
 *       — {@code @EntityGraph(attributePaths = "user")}를 붙인다. 재발급 시 access token을 만들려면
 *       User가 <b>항상</b> 필요해 LAZY로 두면 N+1이 확정이다.</li>
 *   <li>{@code int deleteByTokenHash(String)} — 로그아웃. 해당 기기 1건만.</li>
 *   <li>{@code int deleteAllByUserId(Long)} — 탈퇴. 해당 유저 전체.
 *       {@code @ManyToOne User user}의 속성 경로로 파생되므로 {@code User} 엔티티를 로딩할 필요가 없다.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * 벌크 삭제에는 {@code @Modifying(clearAutomatically = true)}를 붙인다.
 * 탈퇴 흐름에서 {@code user.withdraw()}와 이 삭제는 <b>서로 다른 트랜잭션</b>에서 실행되므로
 * ({@code UserController}가 순서를 소유) 더티 체킹 flush 순서를 걱정할 필요가 없고,
 * {@code flushAutomatically}도 필요 없다.
 */
public interface RefreshTokenRepository {
}
