package com.toyproject.backend.domain.auth.repository;

import com.toyproject.backend.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * [Phase 2 · 3단계] 재발급 시 토큰 조회, 로그아웃·탈퇴 시 무효화.
 *
 * <p>삭제는 엔티티를 로딩하지 않고 벌크 {@code DELETE}로 한다 — 무효화가 목적이라 엔티티 상태가
 * 필요 없다. 이름 파생 {@code deleteBy...}는 대상을 전부 로딩한 뒤 건건이 지운다.
 * <p>탈퇴 흐름에서 회원 소프트 삭제와 다른 트랜잭션이라 {@code flushAutomatically}가 필요 없다.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 토큰 해시로 발급 이력을 찾는다. 재발급의 진입점이다.
     *
     * <p>인자는 쿠키로 받은 원문이 아니라 그것의 SHA-256 hex 64자다.
     * <p>{@code @EntityGraph}로 {@code user}를 함께 읽는다 — 재발급은 직후 claim을 채워야 해서
     * User가 항상 필요하고, LAZY면 요청마다 SELECT가 한 번 더 나간다. 연관을 EAGER로 바꾸면
     * User가 필요 없는 로그아웃·탈퇴 경로까지 끌려온다.
     *
     * @param tokenHash 토큰 원문의 SHA-256 hex (소문자 64자)
     * @return row가 있어도 유효한 토큰은 아니다. 만료 판정은 호출자가
     *         {@link RefreshToken#isExpired()}로 한다
     */
    @EntityGraph(attributePaths = "user")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 토큰 1건을 무효화한다. 로그아웃 전용이고 요청한 기기의 토큰만 지운다.
     *
     * @return 삭제된 row 수 (0 또는 1). 없는 해시도 예외가 아니라 0이라 멱등하다
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshToken rt where rt.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 한 회원의 토큰을 전부 무효화한다. 탈퇴 전용이다.
     *
     * <p>{@code rt.user.id}로 조건을 걸어 {@code User} 엔티티를 받지 않는다 — 탈퇴 시
     * auth가 회원을 로딩하지 않아도 된다.
     *
     * @return 삭제된 row 수. 회원 소프트 삭제보다 먼저 실행돼야 한다
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshToken rt where rt.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
