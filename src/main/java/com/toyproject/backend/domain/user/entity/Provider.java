package com.toyproject.backend.domain.user.entity;

/**
 * [1단계 · G] 소셜 로그인 제공자 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code (provider, providerId)} 복합 유니크의 한 축. 어느 소셜 계정에서 온 회원인지 식별한다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>현재는 {@code KAKAO} 하나만 정의한다. 구글 등 추가 시 상수만 늘리면 되도록 둔다.</li>
 *   <li>이메일+비밀번호 가입은 범위 밖이므로 {@code LOCAL} 상수를 미리 만들지 않는다
 *       (사용처 없는 상수는 죽은 분기를 만든다).</li>
 * </ul>
 *
 * <p><b>예정 상수</b>: {@code KAKAO}
 */
public enum Provider {
}
