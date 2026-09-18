package com.toyproject.backend.domain.product.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * [Phase 3 · 6단계] 상품 목록 검색 조건
 *
 * <p><b>역할</b><br>
 * <b>서비스가 조립한 내부 검색 조건</b>이다. {@code ProductService} → {@code ProductSpecifications}
 * 구간만 건너며, HTTP 경계를 건너지 않는다. <b>SQL을 모른다</b> — "무엇으로 거를 것인가"만 담고,
 * 그것을 WHERE 절로 바꾸는 일은 {@code ProductSpecifications}가 한다.
 *
 * <p><b>⚠️ 요청 바디/파라미터를 바인딩하는 타입이 아니다.</b> 클라이언트가 보내는 값은
 * {@code ProductSearchRequest}가 받고, <b>카테고리를 펼친 뒤</b> 서비스가 이 record를 만든다.
 * 둘을 나눈 이유는 {@code categoryIds}에 있다 — 바깥에서 오는 것은 <b>단수</b>
 * {@code categoryId}이고 여기 담기는 것은 <b>펼쳐진 목록</b>이라, 한 타입으로 두면
 * "펼치기 전인가 후인가"가 타입에서 드러나지 않는다.
 *
 * <p><b>왜 묶는가</b><br>
 * 값 네 개를 서비스 → Specifications로 그대로 들고 다니면 시그니처가 길어지고,
 * <b>조건이 하나 늘 때마다 그 경로의 모든 시그니처가 바뀐다.</b> record 하나로 묶으면
 * 필드 추가가 record 안에서 끝난다 — 인자 여러 개를 하나로 묶는 파라미터 객체다.
 *
 * <p><b>모든 필드가 nullable이다.</b> "안 보냈다"가 정상 입력이며, 값이 없는 조건은
 * WHERE 절에서 빠진다. 그래서 여기에 {@code @NotNull} 류를 붙이지 않는다 —
 * 형식 검증(가격이 음수인지 등)은 요청을 받는 컨트롤러의 몫이다.
 *
 * @param categoryIds 카테고리 필터. <b>단수가 아니라 목록이다</b> — 대분류 하나를 받아
 *                    {@code [자기 자신 + 자식들]}로 펼친 결과가 여기 들어온다.
 *                    펼치는 책임은 7단계의 {@code CategoryService}이고,
 *                    이 record는 이미 펼쳐진 결과만 받는다
 * @param minPrice    가격 하한(이상). 없으면 {@code null}
 * @param maxPrice    가격 상한(이하). 없으면 {@code null}
 * @param keyword     상품명 부분 일치. {@code null}과 <b>공백 문자열</b> 모두 "조건 없음"이다
 */
public record ProductSearchCondition(

        List<Long> categoryIds,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String keyword
) {
}
