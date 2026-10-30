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
 * [Phase 2 · 9단계] 내 정보 조회·수정·탈퇴. 전부 인증이 필요하다.
 *
 * <p>로그인 사용자는 {@code @AuthenticationPrincipal}로만 식별한다 — 클라이언트가 회원을
 * 지목할 수단이 없어야 소유권 검사라는 개념 자체가 필요 없어진다.
 * <p>탈퇴는 user와 auth 양쪽을 건드리는 유일한 흐름이라 순서를 이 컨트롤러가 소유한다.
 * {@code UserService}가 {@code AuthService}를 참조하지 않게 하려는 것이다.
 * <p>호출 순서와 fail-closed·best-effort 구분은 {@code docs/domains/auth.md}의 "회원 탈퇴".
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
     * <p>{@code @Valid}는 형식만 본다. 모든 필드가 optional이라 빈 바디도 유효한 요청이다.
     */
    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMyInfo(@AuthenticationPrincipal UserPrincipal principal,
                                                  @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.updateMyInfo(principal.getId(), request));
    }

    /**
     * 회원 탈퇴. 응답 바디는 {@code null}이고 후속 조치는 상태 코드만으로 충분하다.
     *
     * <p>아래 세 호출의 순서를 뒤집지 말 것 — 소프트 삭제를 먼저 하면 WITHDRAWN인데
     * refresh token row가 남아 탈퇴한 회원의 세션이 살아 있게 된다.
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
