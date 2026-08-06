package com.toyproject.backend.domain.user.dto;

/**
 * [8단계 · K] 회원 정보 응답 DTO — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code GET /api/users/me}와 {@code PUT /api/users/me}의 공통 응답 바디.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>record + {@code from(User)} 정적 팩터리. 엔티티→DTO 변환 지점을 한 곳에 모은다.</li>
 *   <li>엔티티를 직접 반환하지 않는다. 지연 로딩 직렬화 문제와 내부 필드 노출을 동시에 막는다.</li>
 * </ul>
 *
 * <p><b>예정 컴포넌트</b>: {@code id}, {@code email}, {@code name}, {@code phone}, {@code address},
 * {@code birthDate}, {@code role}, {@code provider}, {@code createdAt}
 *
 * <p><b>주의</b><br>
 * {@code providerId}(카카오 고유 id), {@code status}, {@code withdrawnAt}은 <b>노출하지 않는다.</b>
 * 클라이언트가 쓸 일이 없고, {@code providerId}는 외부 서비스의 식별자다.
 */
public record UserResponse(/* 구현 단계에서 정의 */) {
}
