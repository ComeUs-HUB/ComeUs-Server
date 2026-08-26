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
 * [6단계 · J] 인증 주체
 *
 * <p><b>역할</b><br>
 * {@code SecurityContext}에 담기는 로그인 사용자 표현. 컨트롤러는
 * {@code @AuthenticationPrincipal UserPrincipal}로 <b>일관되게</b> 이 타입을 받는다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>{@code User} 엔티티를 통째로 들고 있지 않는다. 요청 처리 중 detach된 엔티티를 참조하게 되고,
 *       필요 없는 필드까지 요청 수명 내내 붙들게 된다. <b>필요한 최소 정보만</b> 복사한다.</li>
 *   <li>{@link #from(User)} 정적 팩터리로 변환 지점을 한 곳에 모은다.</li>
 *   <li>불변이다. 인증 이후 이 객체를 고쳐 권한을 바꾸는 경로를 만들지 않는다.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * {@code PasswordEncoder} Bean은 <b>만들지 않는다</b>(확정 사항). 카카오 전용이라 비밀번호 개념 자체가 없어
 * {@link #getPassword()}는 {@code null}을 반환한다. 우리는 {@code DaoAuthenticationProvider}를 거치지 않고
 * 필터가 직접 {@code SecurityContext}를 채우므로 비밀번호 대조가 일어나는 지점이 없다.
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

    /**
     * {@code ROLE_} 접두사를 붙인 권한 1개.
     *
     * <p>Spring Security의 {@code hasRole("ADMIN")}이 내부적으로 {@code ROLE_ADMIN}을 찾으므로
     * 접두사를 생략하면 phase6의 ADMIN 권한 체크가 조용히 실패한다.
     */
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

    /**
     * 탈퇴 회원이면 {@code false}.
     *
     * <p>다만 실제 거절은 {@code CustomUserDetailsService}가 {@code DisabledException}을 던져 수행한다
     * — 우리는 {@code DaoAuthenticationProvider}(이 값을 검사하는 주체)를 타지 않기 때문이다.
     * 이 메서드는 인가 단계에서 principal을 다시 들여다볼 때를 위한 표현이다.
     */
    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
