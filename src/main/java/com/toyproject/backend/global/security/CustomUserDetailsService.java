package com.toyproject.backend.global.security;

import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Phase 2 · 6단계] JWT subject(userId)로 회원을 조회해 {@link UserPrincipal}로 변환한다.
 *
 * <p>매 요청 DB를 조회한다. claim만 믿으면 탈퇴 회원이 토큰 만료까지 통과하므로 캐싱하지 않는다.
 * <p>미존재와 탈퇴를 다른 예외로 던진다. 합치면 필터가 A001(401)과 A008(403)을 가르지 못한다.
 * <p>그 구분과 {@code UserRepository} 직접 주입 근거는 ADR 0021.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * @throws UsernameNotFoundException 회원이 없거나 subject가 숫자가 아닐 때 (A001)
     * @throws DisabledException         탈퇴한 회원일 때 (A008)
     */
    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(parseUserId(userId))
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 회원입니다: " + userId));

        if (user.isWithdrawn()) {
            throw new DisabledException("탈퇴한 회원입니다: " + userId);
        }
        return UserPrincipal.from(user);
    }

    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            // 서명을 통과한 토큰만 도달하므로 사실상 발생하지 않지만,
            // 그대로 새어 나가면 500이 된다. 인증 실패로 바꿔 A001로 응답한다.
            throw new UsernameNotFoundException("잘못된 형식의 subject입니다: " + userId, e);
        }
    }
}
