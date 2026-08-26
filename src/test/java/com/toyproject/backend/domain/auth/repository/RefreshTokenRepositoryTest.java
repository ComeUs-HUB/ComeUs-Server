package com.toyproject.backend.domain.auth.repository;

import com.toyproject.backend.domain.auth.entity.RefreshToken;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import com.toyproject.backend.global.config.JpaConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [3단계 · G] {@link RefreshTokenRepository} 검증
 *
 * <p>애노테이션 근거는 {@code UserRepositoryTest}와 동일하다.
 *
 * <p><b>{@code em.flush(); em.clear();}가 필요한 이유</b><br>
 * 영속성 컨텍스트에 이미 올라간 {@code User}는 LAZY여도 재사용되므로, clear 없이는
 * {@code @EntityGraph}가 실제로 동작했는지 구분할 수 없다.
 */
@DataJpaTest
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("RefreshTokenRepository")
class RefreshTokenRepositoryTest {

    /** SHA-256 hex는 항상 64자다. 컬럼이 char(64)라 길이만 맞으면 되고 실제 해시일 필요는 없다. */
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.ofKakao("kakao-1001", "user1@kakao.com", "민준"));
        otherUser = userRepository.save(User.ofKakao("kakao-1002", "user2@kakao.com", "지훈"));
    }

    private RefreshToken issue(User owner, String tokenHash) {
        return refreshTokenRepository.save(
                RefreshToken.issue(owner, tokenHash, LocalDateTime.now().plusDays(7))
        );
    }

    @Test
    @DisplayName("findByTokenHash — 해시로 발급 이력을 찾는다")
    void findByTokenHash_found() {
        issue(user, HASH_A);
        em.flush();
        em.clear();

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(HASH_A);

        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo(HASH_A);
    }

    @Test
    @DisplayName("findByTokenHash — 없는 해시는 빈 결과다 (재발급 시 A007)")
    void findByTokenHash_notFound() {
        issue(user, HASH_A);
        em.flush();
        em.clear();

        assertThat(refreshTokenRepository.findByTokenHash(HASH_B)).isEmpty();
    }

    @Test
    @DisplayName("findByTokenHash — @EntityGraph가 user를 함께 로딩한다 (N+1 방지)")
    void findByTokenHash_fetchesUserEagerly() {
        issue(user, HASH_A);
        em.flush();
        em.clear();

        RefreshToken found = refreshTokenRepository.findByTokenHash(HASH_A).orElseThrow();

        PersistenceUnitUtil unitUtil = em.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(unitUtil.isLoaded(found.getUser())).isTrue();
        assertThat(found.getUser().getProviderId()).isEqualTo("kakao-1001");
    }

    @Test
    @DisplayName("만료된 토큰도 row는 조회된다 — 유효성 판정은 expiresAt이 담당한다")
    void expiredToken_isStillFound() {
        refreshTokenRepository.save(
                RefreshToken.issue(user, HASH_A, LocalDateTime.now().minusSeconds(1))
        );
        em.flush();
        em.clear();

        RefreshToken found = refreshTokenRepository.findByTokenHash(HASH_A).orElseThrow();

        // row 존재만 보면 만료 토큰이 영구히 유효해진다. 거절은 isExpired()가 한다.
        assertThat(found.isExpired()).isTrue();
    }

    @Test
    @DisplayName("token_hash 중복 저장은 유니크 제약으로 차단된다")
    void duplicateTokenHash_rejected() {
        issue(user, HASH_A);
        em.flush();

        assertThatThrownBy(() -> {
            refreshTokenRepository.save(
                    RefreshToken.issue(otherUser, HASH_A, LocalDateTime.now().plusDays(7))
            );
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deleteByTokenHash — 해당 기기 1건만 지우고 다른 기기 세션은 유지한다")
    void deleteByTokenHash_deletesOnlyThatDevice() {
        issue(user, HASH_A);
        issue(user, HASH_B);
        em.flush();

        int deleted = refreshTokenRepository.deleteByTokenHash(HASH_A);

        assertThat(deleted).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash(HASH_A)).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(HASH_B)).isPresent();
    }

    @Test
    @DisplayName("deleteByTokenHash — 없는 해시는 예외 없이 0을 반환한다 (로그아웃 멱등성)")
    void deleteByTokenHash_isIdempotent() {
        issue(user, HASH_A);
        em.flush();

        assertThat(refreshTokenRepository.deleteByTokenHash(HASH_A)).isEqualTo(1);
        assertThat(refreshTokenRepository.deleteByTokenHash(HASH_A)).isZero();
    }

    @Test
    @DisplayName("deleteAllByUserId — 해당 유저 전수 삭제, 다른 유저 토큰은 보존한다")
    void deleteAllByUserId_deletesOnlyOwnTokens() {
        issue(user, HASH_A);
        issue(user, HASH_B);
        issue(otherUser, HASH_C);
        em.flush();

        int deleted = refreshTokenRepository.deleteAllByUserId(user.getId());

        assertThat(deleted).isEqualTo(2);
        assertThat(refreshTokenRepository.findAll())
                .hasSize(1)
                .allSatisfy(token -> assertThat(token.getTokenHash()).isEqualTo(HASH_C));
    }

    @Test
    @DisplayName("deleteAllByUserId — 로그인 기기가 없던 회원은 0을 반환한다")
    void deleteAllByUserId_noTokens() {
        assertThat(refreshTokenRepository.deleteAllByUserId(user.getId())).isZero();
    }

    @Test
    @DisplayName("저장 시 auditing이 createdAt을 채운다")
    void auditingFieldIsPopulated() {
        RefreshToken saved = issue(user, HASH_A);
        em.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
