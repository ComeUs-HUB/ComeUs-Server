package com.toyproject.backend.domain.user.service;

/**
 * [7단계 · K] 회원 서비스 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * 내 정보 조회·수정·탈퇴.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code userId}는 {@code @AuthenticationPrincipal}에서 받은 값이므로 소유권 검사가 따로 필요 없다
 *       (자기 자신만 다루는 API다).</li>
 *   <li>수정은 엔티티의 {@code updateProfile}에 위임하고 더티 체킹으로 반영한다.
 *       서비스에서 필드를 직접 세팅하지 않는다.</li>
 * </ul>
 *
 * <p><b>예정 메서드</b>
 * <ul>
 *   <li>{@code UserResponse getMyInfo(Long userId)}</li>
 *   <li>{@code UserResponse updateMyInfo(Long userId, UserUpdateRequest)} — null 필드는 미변경</li>
 *   <li>{@code void withdraw(Long userId)}</li>
 * </ul>
 *
 * <p><b>탈퇴 처리 순서</b>
 * <ol>
 *   <li>{@code user.withdraw()} — 소프트 삭제({@code status=WITHDRAWN}, {@code withdrawnAt} 기록)</li>
 *   <li>{@code deleteAllByUser(user)} — 해당 유저 refresh token 전체 삭제(모든 기기 로그아웃)</li>
 *   <li>카카오 {@code unlink} — <b>best-effort</b></li>
 * </ol>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>카카오 {@code unlink}는 실패해도 <b>로그만 남기고 탈퇴를 계속 진행</b>한다.
 *       카카오 장애로 탈퇴가 막히면 안 된다. 예외를 삼키는 판단은 클라이언트가 아니라 여기서 한다.</li>
 *   <li>{@code unlink}에 필요한 {@code providerId}는 <b>벌크 삭제 전에</b> 지역 변수로 확보해 둔다.
 *       {@code clearAutomatically=true}가 영속성 컨텍스트를 비워 엔티티가 detach되기 때문이다.</li>
 *   <li>탈퇴 회원 조회는 "없음"으로 취급한다(이미 탈퇴한 계정에 다시 접근하는 경우).</li>
 * </ul>
 */
public class UserService {
}
