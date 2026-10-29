package com.toyproject.backend.global.security;

import com.toyproject.backend.domain.user.entity.Role;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.entity.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * [Phase 2 · 6단계] {@code SecurityContext}에 담기는 로그인 사용자. 컨트롤러는
 * {@code @AuthenticationPrincipal UserPrincipal}로 이 타입을 받는다.
 *
 * <p>{@code User} 엔티티를 들고 있지 않고 최소 정보만 복사한다 — detach된 엔티티를 요청 수명
 * 내내 붙들지 않기 위해서다.
 * <p>{@code PasswordEncoder} Bean은 만들지 않는다. 카카오 전용이라 대조 지점이 없다(ADR 0021).
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final Role role;
    private final UserStatus status;

    private UserPrincipal(Long id, Role role, UserStatus status) {
        this.id = id;
        this.role = role;
        this.status = status;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getRole(), user.getStatus());
    }

    /** {@code hasRole("ADMIN")}이 {@code ROLE_ADMIN}을 찾으므로 접두사를 생략하면 조용히 실패한다. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** 카카오 전용이라 비밀번호가 존재하지 않는다. */
    @Override
    public String getPassword() {
        return null;
    }

    /** JWT subject와 대응된다. {@code CustomUserDetailsService}가 이 값으로 조회한다. */
    @Override
    public String getUsername() {
        return String.valueOf(id);
    }

    /** 호출자가 없다 — 실제 거절은 {@code CustomUserDetailsService}가 한다(ADR 0021). */
    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
