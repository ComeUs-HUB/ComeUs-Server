package com.toyproject.backend.domain.user.controller;

import com.toyproject.backend.domain.auth.service.AuthService;
import com.toyproject.backend.domain.user.dto.UserResponse;
import com.toyproject.backend.domain.user.dto.UserUpdateRequest;
import com.toyproject.backend.domain.user.service.UserService;
import com.toyproject.backend.global.response.ApiResponse;
import com.toyproject.backend.global.security.UserPrincipal;
import com.toyproject.backend.global.util.CookieUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [9단계 · K] 회원 API
 *
 * <p><b>역할</b><br>
 * 로그인한 사용자의 내 정보 조회·수정·탈퇴 엔드포인트.
 *
 * <p><b>API</b>
 * <table border="1">
 *   <tr><th>Method</th><th>URI</th><th>요청</th><th>응답</th></tr>
 *   <tr><td>GET</td><td>{@code /api/users/me}</td><td>-</td>
 *       <td>{@code {id, email, name, phone, address, birthDate, role, provider, createdAt}}</td></tr>
 *   <tr><td>PUT</td><td>{@code /api/users/me}</td><td>{@code {name?, phone?, address?, birthDate?}} 모두 optional</td>
 *       <td>위 GET과 동일</td></tr>
 *   <tr><td>DELETE</td><td>{@code /api/users/me}</td><td>-</td><td>{@code null} + 쿠키 만료 {@code Set-Cookie}</td></tr>
 * </table>
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>전부 인증이 필요하다. {@code SecurityConfig}의 {@code anyRequest().authenticated()}에 포함되므로
 *       별도 선언이 없다.</li>
 *   <li>로그인 사용자 식별은 {@code @AuthenticationPrincipal UserPrincipal}로 <b>일관되게</b> 한다.
 *       경로 변수나 요청 바디의 userId를 신뢰하지 않는다 — <b>클라이언트가 회원을 지목할 수단이
 *       아예 없어야</b> 소유권 검사라는 개념 자체가 필요 없어진다.</li>
 *   <li>PUT은 {@code @Valid}로 형식만 검증한다. 실패는 {@code GlobalExceptionHandler}가 C001로 처리한다.</li>
 * </ul>
 *
 * <p><b>탈퇴(DELETE) 호출 순서</b><br>
 * user와 auth 양쪽을 건드리는 <b>유일한 흐름</b>이다. 순서를 <b>이 컨트롤러가 소유</b>해
 * {@code UserService}가 {@code AuthService}를 참조하지 않게 한다. 그 결과 <b>도메인 계층</b>의
 * 의존 방향이 {@code auth → user} 단방향으로 유지된다 — 컨트롤러는 유스케이스를 조립하는
 * 자리라 양쪽을 다 알아도 되지만, 서비스끼리 얽히면 되돌리기 어렵다.
 * <ol>
 *   <li>{@code authService.revokeAllTokens(userId)} — refresh token 전수 삭제.
 *       <b>fail-closed</b>: 실패하면 예외가 전파되어 탈퇴가 중단되고 회원은 ACTIVE로 남는다
 *       (재로그인으로 복구 가능)</li>
 *   <li>{@code String providerId = userService.withdraw(userId)} — 소프트 삭제 + {@code providerId} 획득</li>
 *   <li>{@code authService.unlinkKakao(providerId)} — 카카오 연결 해제. <b>best-effort</b>,
 *       실패해도 로그만 남고 탈퇴는 성립한다</li>
 *   <li>{@code refresh_token} 쿠키 만료</li>
 * </ol>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>⚠️ <b>순서를 뒤집지 말 것.</b> 2를 먼저 하면 WITHDRAWN인데 refresh token row가 남는
 *       상태가 생긴다 — 탈퇴한 회원의 세션이 살아 있는 셈이다.</li>
 *   <li>1과 2는 <b>별도 트랜잭션</b>이다. 원자적이지 않지만 어느 쪽이 실패해도 결과가 fail-safe하고,
 *       카카오 {@code unlink}는 외부 호출이라 애초에 트랜잭션으로 묶을 수 없다.
 *       원자성이 필요해지면 도메인 이벤트로 전환한다(현 단계에서는 도입하지 않음).</li>
 *   <li>탈퇴는 발급된 refresh token이 전부 삭제되므로 <b>쿠키도 함께 만료</b>시켜야 한다.
 *       이때도 {@code Path=/api/auth}를 함께 지정하지 않으면 브라우저에서 실제로 지워지지 않는다
 *       ({@code CookieUtil}이 담당한다).</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final CookieUtil cookieUtil;

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(userService.getMyInfo(principal.getId()));
    }

    /**
     * 프로필 부분 수정. 보내지 않은 필드는 그대로 둔다.
     *
     * <p>{@code @Valid}가 검증하는 것은 <b>형식뿐</b>이다. 모든 필드가 optional이라
     * 빈 바디({@code {}})도 유효한 요청이며, 아무것도 바꾸지 않고 현재 정보를 반환한다.
     */
    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMyInfo(@AuthenticationPrincipal UserPrincipal principal,
                                                  @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.updateMyInfo(principal.getId(), request));
    }

    /**
     * 회원 탈퇴. 위 "탈퇴 호출 순서" 참고.
     *
     * <p>응답 바디는 {@code null}이다. 탈퇴 결과로 클라이언트에 돌려줄 데이터가 없고,
     * 필요한 후속 조치(로그인 화면 이동)는 상태 코드만으로 충분하다.
     */
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal.getId();

        authService.revokeAllTokens(userId);
        String providerId = userService.withdraw(userId);
        authService.unlinkKakao(providerId);

        log.info("회원 탈퇴 완료 — userId={}", userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.deleteRefreshTokenCookie().toString())
                .body(ApiResponse.success(null));
    }
}
