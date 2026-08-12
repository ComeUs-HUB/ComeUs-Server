package com.toyproject.backend.domain.user.repository;

import com.toyproject.backend.domain.user.entity.Provider;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.global.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [3단계 · G] {@link UserRepository} 검증
 *
 * <p><b>애노테이션 근거</b>
 * <ul>
 *   <li>{@code @Import(JpaConfig.class)} — {@code @DataJpaTest}는 일반 {@code @Configuration}을
 *       스캔하지 않아 {@code @EnableJpaAuditing}이 켜지지 않는다. {@code createdAt}/{@code updatedAt}이
 *       {@code nullable = false}라 auditing 없이는 첫 {@code save}부터 NOT NULL 위반으로 깨진다.</li>
 *   <li>{@code @AutoConfigureTestDatabase(replace = NONE)} — 기본값은 설정된 DataSource를 임베디드로
 *       교체하는 것이라 {@code src/test/resources/application.yml}의 {@code MODE=MySQL}이 무시된다.</li>
 * </ul>
 */
@DataJpaTest
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserRepository")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("findByProviderAndProviderId — 가입 이력이 있으면 기존 회원을 반환한다")
    void findByProviderAndProviderId_found() {
        userRepository.save(User.ofKakao("kakao-1001", "user1@kakao.com", "민준"));

        Optional<User> found = userRepository.findByProviderAndProviderId(Provider.KAKAO, "kakao-1001");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("user1@kakao.com");
        assertThat(found.get().getProviderId()).isEqualTo("kakao-1001");
    }

    @Test
    @DisplayName("findByProviderAndProviderId — providerId가 다르면 빈 결과다 (신규 가입으로 분기)")
    void findByProviderAndProviderId_notFound() {
        userRepository.save(User.ofKakao("kakao-1001", "user1@kakao.com", "민준"));

        Optional<User> found = userRepository.findByProviderAndProviderId(Provider.KAKAO, "kakao-9999");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByProviderAndProviderId — 탈퇴 회원도 조회된다 (재로그인 시 재활성화 대상)")
    void findByProviderAndProviderId_includesWithdrawn() {
        User user = User.ofKakao("kakao-1001", "user1@kakao.com", "민준");
        user.withdraw();
        userRepository.save(user);

        Optional<User> found = userRepository.findByProviderAndProviderId(Provider.KAKAO, "kakao-1001");

        assertThat(found).isPresent();
        assertThat(found.get().isWithdrawn()).isTrue();
    }

    @Test
    @DisplayName("existsByEmail — 사용 중인 주소는 true, 미사용은 false")
    void existsByEmail() {
        userRepository.save(User.ofKakao("kakao-1001", "user1@kakao.com", "민준"));

        assertThat(userRepository.existsByEmail("user1@kakao.com")).isTrue();
        assertThat(userRepository.existsByEmail("nobody@kakao.com")).isFalse();
    }

    @Test
    @DisplayName("(provider, providerId) 중복 가입은 유니크 제약으로 차단된다")
    void duplicateProviderAndProviderId_rejected() {
        userRepository.saveAndFlush(User.ofKakao("kakao-1001", "user1@kakao.com", "민준"));

        // saveAndFlush 여야 한다. save만 하면 flush가 트랜잭션 끝까지 지연돼 예외가 이 블록 밖에서 터진다.
        assertThatThrownBy(() ->
                userRepository.saveAndFlush(User.ofKakao("kakao-1001", "other@kakao.com", "다른사람"))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("email이 null인 회원은 여러 명 가입할 수 있다 (유니크 컬럼이지만 NULL 중복 허용)")
    void multipleNullEmails_allowed() {
        userRepository.saveAndFlush(User.ofKakao("kakao-1001", null, "민준"));

        // 카카오 이메일 미동의 회원이 2명 이상 가입 가능해야 한다는 설계 전제를 못 박는다.
        assertThatCode(() ->
                userRepository.saveAndFlush(User.ofKakao("kakao-1002", null, "지훈"))
        ).doesNotThrowAnyException();

        assertThat(userRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("email 중복은 유니크 제약으로 차단된다 (가입 시 email을 null로 두는 근거)")
    void duplicateEmail_rejected() {
        userRepository.saveAndFlush(User.ofKakao("kakao-1001", "same@kakao.com", "민준"));

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(User.ofKakao("kakao-1002", "same@kakao.com", "지훈"))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("저장 시 auditing이 createdAt/updatedAt을 채운다")
    void auditingFieldsArePopulated() {
        User saved = userRepository.saveAndFlush(User.ofKakao("kakao-1001", "user1@kakao.com", "민준"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
