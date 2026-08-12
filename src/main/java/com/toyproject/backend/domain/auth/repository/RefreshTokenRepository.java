package com.toyproject.backend.domain.auth.repository;

import com.toyproject.backend.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * [3단계 · G] 리프레시 토큰 Repository
 *
 * <p><b>역할</b><br>
 * 재발급 시 토큰 조회, 로그아웃/탈퇴 시 토큰 무효화를 담당한다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>삭제는 엔티티를 로딩한 뒤 지우지 않고 <b>벌크 삭제</b>({@code @Modifying @Query})로 처리한다.
 *       무효화가 목적이라 엔티티 상태가 필요 없다. 이름 파생 {@code deleteBy...}를 쓰면 대상을 전부
 *       로딩한 뒤 건건이 {@code DELETE}를 날리므로 의도적으로 {@code @Query}를 명시했다.</li>
 *   <li>벌크 삭제에는 {@code @Modifying(clearAutomatically = true)}만 붙인다.
 *       탈퇴 흐름에서 {@code user.withdraw()}와 이 삭제는 <b>서로 다른 트랜잭션</b>에서 실행되므로
 *       ({@code UserController}가 순서를 소유) 더티 체킹 flush 순서를 걱정할 필요가 없고,
 *       {@code flushAutomatically}도 필요 없다.</li>
 * </ul>
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 토큰 해시로 발급 이력을 찾는다. <b>재발급({@code POST /api/auth/refresh})의 진입점</b>이다.
     *
     * <p>인자는 쿠키로 받은 <b>원문이 아니라 그것을 SHA-256으로 해시한 hex 64자</b>다.
     * 서버는 원문을 보관하지 않으므로 조회 전에 반드시 해시해야 한다.
     *
     * <p>{@code @EntityGraph}로 {@code user}를 함께 가져오는 이유: 재발급은 조회 직후
     * {@code createAccessToken(User)}로 subject·role claim을 채워야 해서 User가 <b>항상</b> 필요하다.
     * LAZY 프록시로 두면 {@code getRole()}을 읽는 시점에 SELECT가 한 번 더 나가
     * <b>요청 1회당 쿼리가 2회</b>가 된다({@code getId()}만 읽으면 FK로 해결돼 쿼리가 나가지 않는다).
     * {@code token_hash}가 유니크라 결과가 1건이므로 <b>엄밀한 N+1은 아니다.</b> 다만 재발급은
     * 앱 부팅·access token 만료(30분)마다 호출되는 최다 호출 엔드포인트라 1회 차이가 누적된다.
     *
     * <p>연관을 EAGER로 바꾸지 않는 이유: 로그아웃·탈퇴 경로는 User가 필요 없는데 EAGER는 엔티티 전역이라
     * 끌 수 없다. 필드는 LAZY로 두고 <b>필요한 쿼리에만</b> {@code @EntityGraph}로 예외를 준다.
     *
     * <p>⚠️ <b>row가 존재한다고 유효한 토큰이 아니다.</b> 유효성은
     * {@code row 존재 AND expiresAt > now()}이며, 만료 판정은 호출자가
     * {@link RefreshToken#isExpired()}로 수행한다. 미존재와 만료 모두 {@code A007}로 응답한다.
     *
     * @param tokenHash 토큰 원문의 SHA-256 hex (소문자 64자)
     * @return 발급 이력이 있으면 {@code user}가 로딩된 토큰, 없으면 {@link Optional#empty()}
     */
    @EntityGraph(attributePaths = "user")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 토큰 1건을 무효화한다. <b>로그아웃({@code POST /api/auth/logout}) 전용</b>이다.
     *
     * <p>{@code token_hash}가 유니크이므로 삭제 건수는 0 또는 1이다.
     * 멀티 디바이스 로그인이 전제라 <b>요청한 기기의 토큰만</b> 지우고 다른 기기 세션은 유지한다.
     *
     * <p>없는 해시를 넘겨도 예외가 아니라 {@code 0}을 반환한다 — 이미 로그아웃된 상태에서 다시
     * 호출해도 성공해야 하는 <b>멱등성</b>의 근거다.
     *
     * @param tokenHash 토큰 원문의 SHA-256 hex
     * @return 삭제된 row 수 (0 또는 1)
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshToken rt where rt.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 한 회원의 토큰을 전부 무효화한다. <b>회원 탈퇴({@code DELETE /api/users/me}) 전용</b>이다.
     *
     * <p>모든 기기의 세션을 끊는 것이 목적이라 단일 벌크 {@code DELETE}로 처리한다.
     * 조건에 {@code rt.user.id}(연관 엔티티의 속성 경로)를 쓰므로 {@code User} 엔티티를 인자로 받지 않고,
     * 그 결과 <b>탈퇴 시 auth 도메인이 회원을 로딩할 필요가 없다</b>({@code auth → user} 의존을 얇게 유지).
     *
     * <p>⚠️ 탈퇴 오케스트레이션에서 <b>이 삭제가 회원 소프트 삭제보다 먼저</b> 실행돼야 한다.
     * 순서가 뒤집히면 WITHDRAWN인데 {@code refresh_tokens} row가 남는 상태가 생긴다.
     *
     * @param userId 탈퇴하는 회원의 id
     * @return 삭제된 row 수 (로그인 기기가 없었다면 0)
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshToken rt where rt.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
