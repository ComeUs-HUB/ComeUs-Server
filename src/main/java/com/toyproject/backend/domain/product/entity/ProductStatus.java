package com.toyproject.backend.domain.product.entity;

/**
 * [Phase 3 · 5단계] 상품 상태
 *
 * <p><b>역할</b><br>
 * 상품이 팔리는 중인지, 재고가 비었는지, 지워졌는지를 한 값으로 표현한다.
 *
 * <p><b>축이 둘이다</b><br>
 * {@code ON_SALE} ↔ {@code SOLD_OUT}은 <b>재고 축</b>이고 {@code HIDDEN}은 <b>삭제 축</b>이다.
 * 한 필드에 담겨 있지만 서로 독립이라, 재고가 찬다고 해서 {@code HIDDEN}이 풀리지 않는다
 * ({@link Product#increaseStock(int)} 참고). 둘을 같은 판정으로 뭉개면
 * <b>삭제된 상품이 재고 복구만으로 목록에 다시 뜬다.</b>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>{@code @Enumerated(STRING)}만 붙이면 MySQL 방언이 네이티브 {@code enum(...)} 컬럼을 만들어
 *       마이그레이션의 {@code VARCHAR(20)}과 어긋난다({@code ddl-auto: validate} 실패).
 *       {@code @JdbcTypeCode(SqlTypes.VARCHAR)}로 고정한다 — {@code Role}·{@code UserStatus}와 같다.</li>
 *   <li>{@code SOLD_OUT}은 서비스가 정하는 값이 아니라 <b>재고에서 파생되는 값</b>이다.
 *       엔티티 밖에서 이 상수를 직접 세팅하는 코드를 만들지 않는다.</li>
 * </ul>
 */
public enum ProductStatus {

    /** 판매 중. 재고가 1 이상이고 지워지지 않았다. */
    ON_SALE,

    /** 품절. 재고가 0이다. 재고가 다시 차면 {@code ON_SALE}로 돌아온다. */
    SOLD_OUT,

    /**
     * 숨김 — <b>soft delete</b>다. 행은 남고 공개 조회에서만 사라진다.
     *
     * <p>주문 이력에 연결된 상품이 사라지면 안 되기 때문이다. 카테고리를 하드 삭제로 둔 것과
     * 갈리는 지점이며, 여기서 빠져나오는 유일한 통로는 ADMIN의 수정 요청이다.
     */
    HIDDEN
}
