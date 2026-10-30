package com.toyproject.backend.domain.user.repository;

import com.toyproject.backend.domain.user.entity.Provider;
import com.toyproject.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [Phase 2 · 3단계] {@code User}의 영속성 경계. 로그인 시 기존 회원 여부를 판정한다.
 *
 * <p>user 패키지 안에서만 주입한다 — 회원 생성 규칙이 한 곳에 남도록 다른 도메인은
 * {@code UserService}를 경유한다(ADR 0021은 {@code global/security}만의 예외다).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 카카오 계정으로 기존 회원을 찾는다. 로그인 흐름의 진입점이다.
     *
     * <p>조회 키가 복합 유니크와 같아 결과는 0 또는 1건이다.
     * <p>탈퇴 회원도 함께 조회된다 — WITHDRAWN을 찾아 재활성화하는 것이 호출부의 목적이다.
     */
    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    /**
     * 이메일이 이미 사용 중인지 확인한다. 신규 가입 시 email 채택 여부 판단용이다.
     *
     * <p>중복이어도 가입을 막지 않는다 — 카카오 계정 자체는 유일하므로 email만 비우고 진행한다.
     * <p>인자가 {@code null}이면 {@code email = null}이 되어 항상 {@code false}다.
     * 호출 자체를 건너뛰는 판단은 {@code UserService}가 한다.
     */
    boolean existsByEmail(String email);
}
