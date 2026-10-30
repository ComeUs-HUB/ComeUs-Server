package com.toyproject.backend.domain.user.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * [Phase 2 · 9단계] {@code PUT /api/users/me}의 요청 바디. 모든 필드가 optional이다.
 *
 * <p>{@code @NotNull}을 붙이면 부분 수정이 불가능해진다. Bean Validation은 {@code null}을
 * 검증 대상에서 빼므로 형식 검증만으로 optional과 양립한다.
 * <p>길이 제한은 {@code User}의 컬럼과 맞춘다 — 어긋나면 C001이어야 할 실패가 DB까지 내려가
 * C005로 나간다.
 * <p>"null = 변경하지 않음"이라 이미 채워진 값을 비우는 수단은 이 API에 없다.
 *
 * @param phone 하이픈 유무 모두 허용
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
