package com.toyproject.backend.domain.user.dto;

import com.toyproject.backend.domain.user.entity.Provider;
import com.toyproject.backend.domain.user.entity.Role;
import com.toyproject.backend.domain.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * [Phase 2 · 9단계] {@code GET}·{@code PUT /api/users/me}의 공통 응답 바디.
 *
 * <p>{@code providerId}를 담지 않는다 — 외부 서비스의 사용자 id를 응답으로 흘릴 이유가 없다.
 * <p>{@code status}·{@code withdrawnAt}도 담지 않는다. 이 API에 도달했다는 것 자체가
 * ACTIVE라는 뜻이라 항상 같은 값이다.
 *
 * @param email 카카오 미동의거나 가입 시 중복이었으면 {@code null}
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
