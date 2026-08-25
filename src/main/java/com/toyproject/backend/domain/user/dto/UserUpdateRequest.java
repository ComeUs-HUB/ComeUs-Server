package com.toyproject.backend.domain.user.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * [9단계 · K] 회원 정보 수정 요청 DTO
 *
 * <p><b>역할</b><br>
 * {@code PUT /api/users/me}의 요청 바디.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li><b>모든 필드가 optional</b>이다 — 카카오 로그인만으로 가입되므로 프로필은 나중에
 *       조금씩 채워진다. 한 번에 다 입력하라고 강요하지 않는다.</li>
 *   <li>{@code @NotNull}/{@code @NotBlank}를 붙이지 않는다. 붙이면 부분 수정이 불가능해진다.</li>
 *   <li>형식 검증({@code @Size}, {@code @Pattern}, {@code @Past})만 건다.
 *       <b>Bean Validation은 {@code null}을 검증 대상에서 제외</b>하므로
 *       이 애노테이션들은 "값이 있을 때만" 동작한다 — optional과 형식 검증이 양립한다.</li>
 *   <li>길이 제한은 {@code User} 엔티티의 컬럼 정의와 맞춘다. 어긋나면 DB가
 *       {@code DataIntegrityViolationException}(C005)으로 거절해 <b>C001이어야 할 실패가
 *       C005로 나간다.</b></li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * "null = 변경하지 않음" 규약이므로, 이미 채워진 값을 <b>비우는</b> 수단은 이 API에 없다
 * ({@code User.updateProfile}이 null을 건너뛴다). 필요해지면 별도 설계가 필요하다
 * — 빈 문자열과 null의 구분, 또는 명시적 clear 필드.
 *
 * @param name      최대 50자 ({@code users.name}과 동일)
 * @param phone     하이픈 유무 모두 허용
 * @param address   최대 255자 ({@code users.address}와 동일)
 * @param birthDate 과거 날짜만
 */
public record UserUpdateRequest(

        @Size(max = 50, message = "이름은 50자를 넘을 수 없습니다.")
        String name,

        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
        String phone,

        @Size(max = 255, message = "주소는 255자를 넘을 수 없습니다.")
        String address,

        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        LocalDate birthDate
) {
}
