package com.toyproject.backend.domain.user.repository;

/**
 * [2단계 · G] 리프레시 토큰 Repository — 프로토타입 (구현 전)
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
 *   <li>{@code int deleteAllByUser(User)} — 탈퇴. 해당 유저 전체.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * 벌크 삭제에는 {@code @Modifying(clearAutomatically = true, flushAutomatically = true)}를 붙인다.
 * 탈퇴 흐름은 {@code user.withdraw()}로 더티 체킹된 변경을 남긴 채 이 삭제를 호출하는데,
 * {@code flushAutomatically}가 없으면 <b>상태 변경이 flush되기 전에 삭제가 나가</b> 순서가 뒤엉킨다.
 */
public interface RefreshTokenRepository {
}
