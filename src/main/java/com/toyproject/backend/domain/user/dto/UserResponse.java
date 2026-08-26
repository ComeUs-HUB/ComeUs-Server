package com.toyproject.backend.domain.user.dto;

import com.toyproject.backend.domain.user.entity.Provider;
import com.toyproject.backend.domain.user.entity.Role;
import com.toyproject.backend.domain.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * [9단계 · K] 회원 정보 응답 DTO
 *
 * <p><b>역할</b><br>
 * {@code GET /api/users/me}와 {@code PUT /api/users/me}의 공통 응답 바디.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>{@link #from(User)} 정적 팩터리로 엔티티→DTO 변환 지점을 한 곳에 모은다.</li>
 *   <li>엔티티를 직접 반환하지 않는다. 지연 로딩 직렬화 문제와 내부 필드 노출을 동시에 막는다.</li>
 * </ul>
 *
 * <p><b>주의 — 노출하지 않는 필드</b>
 * <ul>
 *   <li>{@code providerId} — 카카오의 식별자다. 클라이언트가 쓸 일이 없고,
 *       외부 서비스의 사용자 id를 우리 응답으로 흘릴 이유가 없다</li>
 *   <li>{@code status}·{@code withdrawnAt} — 이 API에 도달했다는 것 자체가 ACTIVE라는 뜻이다
 *       (탈퇴 회원은 필터가 A008로 막고, 서비스도 A008을 던진다). 항상 같은 값이라 정보가 없다</li>
 *   <li>{@code updatedAt} — 클라이언트 화면에 쓰이지 않는다</li>
 * </ul>
 *
 * @param email 카카오 이메일 미동의거나 가입 시 중복이었으면 {@code null}
 * @param name  카카오 닉네임이 초기값이며 {@code PUT}으로 바꿀 수 있다
 */
public record UserResponse(

        Long id,
        String email,
        String name,
        String phone,
        String address,
        LocalDate birthDate,
        Role role,
        Provider provider,
        LocalDateTime createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getAddress(),
                user.getBirthDate(),
                user.getRole(),
                user.getProvider(),
                user.getCreatedAt());
    }
}
