package com.toyproject.backend.domain.auth.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * [4단계 · G] 카카오 사용자 정보 응답 DTO
 *
 * <p><b>역할</b><br>
 * {@code GET https://kapi.kakao.com/v2/user/me} 응답 매핑. 회원 생성·조회의 원천 데이터다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>중첩 구조라 {@code KakaoAccount}·{@code Profile}을 중첩 record로 함께 선언한다.
 *       {@code @JsonNaming}은 클래스 단위로 적용되므로 중첩 record에도 각각 붙인다.</li>
 *   <li>깊은 곳의 값을 꺼내는 <b>null 안전 헬퍼</b>를 두어 호출부가 3단 null 체크를 하지 않게 한다.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * 이메일·닉네임은 <b>동의 항목</b>이라 사용자가 거부하면 {@code kakao_account}가 <b>통째로</b> 내려오지 않는다.
 * email이 없다고 가입을 실패시키면 안 된다 — null로 두고 진행한다.
 *
 * @param id           카카오 고유 사용자 id. {@code User.providerId}의 원천
 * @param kakaoAccount 동의 항목 묶음. <b>통째로 null일 수 있다</b>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KakaoUserInfoResponse(

        Long id,
        KakaoAccount kakaoAccount
) {

    /**
     * 카카오 사용자 id를 {@code User.providerId}가 쓰는 문자열로 변환한다.
     *
     * <p>카카오는 숫자로 내려주지만 우리는 provider 중립적인 {@code String}으로 저장한다
     * (구글 등 다른 제공자는 숫자가 아니다).
     *
     * @return id 문자열. id 자체가 없으면 {@code null}
     */
    public String providerId() {
        return id == null ? null : String.valueOf(id);
    }

    /**
     * 이메일을 꺼낸다. <b>이메일 미동의 시 {@code null}</b>이며 이는 정상 경로다.
     *
     * <p>호출부({@code UserService.findOrCreateKakaoUser})는 null이면 email 없이 가입시킨다.
     *
     * @return 이메일. 미동의거나 {@code kakao_account} 자체가 없으면 {@code null}
     */
    public String resolveEmail() {
        return kakaoAccount == null ? null : kakaoAccount.email();
    }

    /**
     * 닉네임을 꺼낸다. {@code User.name}의 초기값이 된다.
     *
     * <p>{@code kakao_account} → {@code profile} → {@code nickname} 3단 중 어느 단계가 비어도 안전하다.
     *
     * @return 닉네임. 미동의거나 상위 구조가 없으면 {@code null}
     */
    public String resolveNickname() {
        if (kakaoAccount == null || kakaoAccount.profile() == null) {
            return null;
        }
        return kakaoAccount.profile().nickname();
    }

    /**
     * 동의 항목 묶음.
     *
     * @param email   이메일. <b>선택 동의라 nullable</b>
     * @param profile 프로필. 미동의 시 null일 수 있다
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record KakaoAccount(String email, Profile profile) {
    }

    /**
     * 프로필.
     *
     * @param nickname 카카오 닉네임
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Profile(String nickname) {
    }
}
