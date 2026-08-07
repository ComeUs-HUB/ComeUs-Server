package com.toyproject.backend.domain.auth.service;

/**
 * [7단계 · I·K] 인증 서비스 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * 로그인·재발급·로그아웃의 비즈니스 로직. HTTP(쿠키·리다이렉트)는 전혀 모르고,
 * 그 조립은 {@code AuthController}가 맡는다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>클래스에 {@code @Transactional(readOnly = true)}, 쓰기 메서드에만 {@code @Transactional}.</li>
 *   <li>refresh token <b>원문은 반환값으로만</b> 흘려보낸다. 필드·로그·응답 DTO에 남기지 않는다.</li>
 *   <li>회원 데이터 접근은 {@code UserService}를 <b>반드시 경유</b>한다.
 *       {@code UserRepository}를 직접 주입하지 않는다 — 회원 생성 규칙이 auth로 새어나오는 것을 막는다.</li>
 * </ul>
 *
 * <p><b>예정 메서드</b>
 * <ul>
 *   <li>{@code String generateOAuthState()} — 인가 요청에 실을 state 생성</li>
 *   <li>{@code KakaoLoginResult kakaoLogin(String code)} — 로그인 본체(아래 흐름 참고)</li>
 *   <li>{@code TokenResponse reissue(String rawRefreshToken)} — 재발급</li>
 *   <li>{@code void logout(String rawRefreshToken)} — 해당 기기 토큰 1건 삭제</li>
 *   <li>{@code Duration getRefreshTokenMaxAge()} — 쿠키 {@code Max-Age}와 {@code expiresAt}이
 *       같은 값을 쓰도록 한 곳에서 제공</li>
 * </ul>
 *
 * <p><b>탈퇴 흐름에서 {@code UserController}가 호출하는 메서드</b>
 * <ul>
 *   <li>{@code void revokeAllTokens(Long userId)} — 해당 유저 {@code RefreshToken} row 전수 삭제.
 *       실패 시 예외를 그대로 던진다(fail-closed — 탈퇴가 중단되어야 한다)</li>
 *   <li>{@code void unlinkKakao(String providerId)} — 카카오 연결 해제.
 *       <b>best-effort</b>: 실패해도 로그만 남기고 예외를 던지지 않는다.
 *       카카오 장애로 탈퇴가 막히면 안 되고, 예외를 삼키는 판단은 호출자가 아니라 여기서 한다</li>
 * </ul>
 * 호출 순서는 {@code UserController}가 소유한다. 여기서 {@code UserService.withdraw}를 부르지 않는다.
 *
 * <p><b>{@code kakaoLogin} 처리 순서</b>
 * <ol>
 *   <li>{@code requestToken(code)} → {@code fetchUserInfo()} → kakaoId, email(nullable), nickname</li>
 *   <li>{@code userService.findOrCreateKakaoUser(kakaoId, email, nickname)}
 *       — 조회·재활성화·신규 생성 판단은 <b>전부 user 도메인이 소유</b>한다.
 *       반환된 {@code KakaoUserResolution}에서 {@code User}와 {@code isNewUser}를 함께 받는다</li>
 *   <li>refresh 원문 생성 → 해시를 {@code RefreshToken} row로 저장 → 원문을 반환</li>
 * </ol>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li><b>여기서 access token을 발급하지 않는다.</b> FE가 복귀 후 {@code POST /api/auth/refresh}로 얻는다.
 *       그 결과 access token이 URL에 실리는 경로가 <b>설계상 존재하지 않고</b>,
 *       로그인 직후와 새로고침 후의 세션 획득 경로가 동일해진다.</li>
 *   <li>재발급 유효성 = <b>row 존재 AND 미만료</b>. 둘 중 하나라도 아니면 {@code REFRESH_TOKEN_NOT_FOUND}(A007).
 *       "없음"과 "만료"를 같은 코드로 응답해 토큰 존재 여부를 흘리지 않는다.</li>
 *   <li>rotation을 하지 않으므로 재발급 시 <b>기존 row를 건드리지 않는다.</b>
 *       여러 탭이 동시에 호출해도 모두 성공해야 한다.</li>
 *   <li>{@code logout}은 쿠키가 없거나 이미 무효여도 <b>예외 없이 성공</b> 처리한다.</li>
 * </ul>
 */
public class AuthService {
}
