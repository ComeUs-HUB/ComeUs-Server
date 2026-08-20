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
 * [6단계 · J] 사용자 조회 서비스
 *
 * <p><b>역할</b><br>
 * JWT에서 꺼낸 userId로 실제 사용자를 조회해 {@link UserPrincipal}로 변환한다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>{@code loadUserByUsername}의 username 자리에는 <b>userId(JWT subject)</b>가 들어온다.</li>
 *   <li><b>매 요청 DB를 조회한다.</b> JWT claim만 믿으면 탈퇴한 회원이 토큰 만료 전까지 계속 통과한다.
 *       캐싱하지 않는 것은 의도된 비용이다.</li>
 * </ul>
 *
 * <p><b>주의 — 거부 사유를 반드시 구분해서 던진다</b>
 * <ul>
 *   <li>사용자 미존재 → {@code UsernameNotFoundException} (→ A001)</li>
 *   <li>{@code WITHDRAWN} → {@code DisabledException} (→ <b>A008, 403</b>)</li>
 * </ul>
 * 둘을 같은 예외로 던지면 필터가 A008(403)과 A001(401)을 갈라낼 근거를 잃고,
 * {@code WITHDRAWN_USER}는 <b>절대 반환되지 않는 죽은 코드</b>가 된다.
 *
 * <p>탈퇴 판정을 {@link UserPrincipal#isEnabled()}에 맡기지 않고 여기서 직접 하는 이유:
 * 그 값을 검사하는 주체는 {@code DaoAuthenticationProvider}인데, 우리 필터는 provider를 거치지 않고
 * {@code SecurityContext}를 직접 채우므로 아무도 그 메서드를 호출하지 않는다.
 *
 * <p><b>⚠️ 결정 필요 — {@code UserRepository} 직접 주입</b><br>
 * {@code docs/SEQUENCE/phase2-auth.md} 173행은 "{@code UserRepository}를 user 패키지 밖에서 주입하지 않는다"고
 * 하지만, 같은 문서 J 항목은 이 클래스가 {@code findById}로 조회한다고 적혀 있고
 * {@code ARCHITECTURE.md}는 {@code global/security}가 "도메인을 참조하는 방향은 허용(프레임워크 결합 지점)"이라고 한다.
 * 다음 근거로 <b>직접 주입</b>을 택했다.
 * <ul>
 *   <li>그 규칙의 목적은 <b>회원 생성 규칙</b>을 user 안에 가두는 것인데(문서 22행),
 *       여기는 생성이 아니라 단순 조회다.</li>
 *   <li>{@code UserService}를 경유하면 6단계가 7단계에 의존해, "각 단계는 그 단계에서 검증이 끝나야 한다"는
 *       순서 결정 원칙(문서 38행)이 깨진다. 6단계를 7단계보다 앞에 둔 근거가 사라진다.</li>
 * </ul>
 * 7단계에서 {@code UserService}에 조회 메서드를 만들 때 이쪽으로 옮길지 함께 결정할 것.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * @param userId JWT subject. {@code JwtProvider.createAccessToken}이 넣은 회원 PK 문자열
     * @throws UsernameNotFoundException 회원이 없거나 subject가 숫자가 아닌 경우 (→ A001)
     * @throws DisabledException         탈퇴한 회원인 경우 (→ A008)
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

    /**
     * subject를 PK로 바꾼다.
     *
     * <p>서명 검증을 통과한 토큰만 여기 도달하므로 형식 오류는 사실상 발생하지 않지만,
     * 그래도 {@code NumberFormatException}이 필터 밖으로 새어 500이 되는 경로를 막는다.
     * <b>인증 실패로 취급</b>해야 A001로 응답된다.
     */
    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new UsernameNotFoundException("잘못된 형식의 subject입니다: " + userId, e);
        }
    }
}
