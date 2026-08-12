package com.toyproject.backend.domain.user.repository;

import com.toyproject.backend.domain.user.entity.Provider;
import com.toyproject.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [3단계 · G] 회원 Repository
 *
 * <p><b>역할</b><br>
 * {@code User}의 영속성 경계. 카카오 로그인 시 "기존 회원인가 신규인가"를 판정하는 조회를 제공한다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>두 메서드 모두 쿼리 메서드 이름만으로 해결되므로 {@code @Query}를 두지 않는다.</li>
 *   <li><b>이 Repository는 user 패키지 안에서만 주입한다.</b> auth 도메인이 회원 데이터에 접근할 때는
 *       {@code UserService}를 경유해야 회원 생성 규칙이 한 곳에 남는다.</li>
 * </ul>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 카카오 계정으로 기존 회원을 찾는다. <b>로그인 흐름의 진입점</b>이다.
     *
     * <p>결과가 있으면 기존 회원을 재사용하고(WITHDRAWN이면 재활성화), 없으면 신규 가입으로 분기한다.
     * 조회 키가 {@code (provider, providerId)} 복합 유니크와 같으므로 결과는 항상 0 또는 1건이다.
     *
     * <p><b>탈퇴 회원도 함께 조회된다.</b> {@code status}를 조건에 넣지 않는 이유는
     * WITHDRAWN 계정을 찾아 재활성화하는 것이 {@code findOrCreateKakaoUser}의 목적이기 때문이다.
     *
     * @param provider   소셜 제공자 (현재는 {@code KAKAO}뿐)
     * @param providerId 카카오 고유 사용자 id
     * @return 가입 이력이 있으면 해당 회원, 없으면 {@link Optional#empty()}
     */
    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    /**
     * 이메일이 이미 사용 중인지 확인한다. <b>신규 가입 시 email 채택 여부 판단용</b>이다.
     *
     * <p>중복이어도 <b>가입을 막지 않는다</b> — 카카오 계정 자체는 유일하므로 거부할 이유가 없고,
     * email만 {@code null}로 두고 가입을 계속 진행한다.
     *
     * <p>⚠️ 인자가 {@code null}이면 SQL이 {@code email = null}이 되어 <b>항상 false</b>를 반환한다.
     * 카카오가 이메일을 주지 않은 경우 호출 자체를 건너뛰어야 하며, 그 판단은 {@code UserService}가 한다.
     *
     * @param email 카카오에서 받은 이메일
     * @return 이미 다른 회원이 쓰고 있으면 {@code true}
     */
    boolean existsByEmail(String email);
}
