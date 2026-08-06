package com.toyproject.backend.domain.user.repository;

/**
 * [2단계 · G] 회원 Repository — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code User}의 영속성 경계. 카카오 로그인 시 "기존 회원인가 신규인가"를 판정하는 조회를 제공한다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code JpaRepository<User, Long>}를 확장한다. 쿼리 메서드 이름만으로 해결되므로
 *       {@code @Query}는 두지 않는다.</li>
 * </ul>
 *
 * <p><b>예정 메서드</b>
 * <ul>
 *   <li>{@code Optional<User> findByProviderAndProviderId(Provider, String)}<br>
 *       — 로그인 진입점. 결과가 있으면 기존 회원 재사용, 없으면 신규 가입.</li>
 *   <li>{@code boolean existsByEmail(String)}<br>
 *       — 신규 가입 시 이메일 중복 확인용. 중복이면 <b>가입을 막지 않고 email만 null로 두고 진행</b>한다
 *       (카카오 계정 자체는 유일하므로 가입을 거부할 이유가 없다).</li>
 * </ul>
 */
public interface UserRepository {
}
