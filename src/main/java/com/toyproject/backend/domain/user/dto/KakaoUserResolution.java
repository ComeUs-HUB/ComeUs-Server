package com.toyproject.backend.domain.user.dto;

import com.toyproject.backend.domain.user.entity.User;

/**
 * [7단계 · K] 카카오 회원 조회/생성 결과
 *
 * <p><b>역할</b><br>
 * {@code UserService.findOrCreateKakaoUser}의 반환 타입. user 도메인이 auth 도메인에게
 * "이 회원이다 + 이번에 새로 만들었다/아니다"를 함께 알려주는 내부 전달용 record다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>신규 여부를 아는 쪽은 회원을 만든 user 도메인뿐이다. auth가 {@code createdAt} 비교 같은
 *       추측으로 판정하지 않도록 값으로 명시해 넘긴다.</li>
 *   <li>{@code AuthService}는 이 값을 {@code KakaoLoginResult.isNewUser}로 그대로 옮기고,
 *       {@code AuthController}가 FE 복귀 URL의 {@code ?isNewUser=true}로 내보낸다.</li>
 *   <li>{@code boolean}을 직접 넘기는 생성자 호출은 호출부에서 {@code true}/{@code false}의 뜻이
 *       보이지 않으므로, 아래 두 정적 팩터리만 쓴다.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * HTTP 응답 바디에 직렬화되는 DTO가 아니다. 엔티티({@code User})를 그대로 담고 있으므로
 * 컨트롤러 밖으로 나가지 않는다.
 *
 * @param user      조회했거나 새로 만든 회원
 * @param isNewUser 이번 호출에서 신규 생성되었으면 {@code true}
 */
public record KakaoUserResolution(User user, boolean isNewUser) {

    /**
     * 기존 회원을 찾은 경우.
     *
     * <p>WITHDRAWN 계정을 재활성화한 경우도 <b>여기</b>다 — 가입 이력이 있는 회원이므로
     * {@code isNewUser=false}이고, FE에서 "추가 정보 입력" UX가 다시 뜨지 않는다.
     */
    public static KakaoUserResolution existing(User user) {
        return new KakaoUserResolution(user, false);
    }

    /** 이번 로그인에서 회원 레코드를 새로 만든 경우. */
    public static KakaoUserResolution created(User user) {
        return new KakaoUserResolution(user, true);
    }
}
