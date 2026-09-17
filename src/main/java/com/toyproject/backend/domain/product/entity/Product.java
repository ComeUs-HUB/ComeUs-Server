package com.toyproject.backend.domain.product.entity;

import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.product.exception.ProductErrorCode;
import com.toyproject.backend.global.exception.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * [Phase 3 · 5단계] 상품 엔티티
 *
 * <p><b>역할</b><br>
 * 판매 대상의 정보와 재고를 보관한다. Phase 4(Cart·Order)가 이 엔티티의 재고를 깎고 되돌린다.
 *
 * <p><b>설계 — 재고와 {@code status}는 함께 움직인다</b><br>
 * Setter를 만들지 않고, <b>재고를 바꾸는 메서드가 {@code status}까지 확정한다.</b>
 * 판정은 {@link #applyStockStatus()} 하나에 모여 있다. 서비스가 재고만 바꾸고 상태를 잊는 조합이
 * 아예 생기지 않는다 — {@code Category.child()}가 2depth를 생성 지점 하나로 강제한 것과 같은 방식이다.
 *
 * <p>재고를 만지는 경로는 Phase 4에서 <b>주문·취소·결제 실패</b>로 늘어난다. 판정이 호출자마다
 * 반복되면 한 곳은 반드시 빠지고, 그때 생기는 것이 <b>재고 0인 {@code ON_SALE}</b> 상품이다.
 *
 * <p><b>⚠️ {@link #increaseStock(int)}과 {@link #updateInfo}가 정반대로 동작한다</b><br>
 * 둘 다 재고를 바꾸지만 {@code HIDDEN} 처리가 갈린다. 의도된 비대칭이며 이 클래스에서 가장
 * 틀리기 쉬운 지점이다.
 * <ul>
 *   <li>{@code increaseStock}은 {@code HIDDEN}을 <b>풀지 않는다.</b> 풀면 Phase 4의 주문 취소·결제
 *       실패가 재고를 되돌릴 때 <b>soft delete된 상품이 목록에 다시 뜬다.</b> 그 시점에는 상품 코드를
 *       건드리지도 않았으므로 원인을 찾기 어렵다 (ADR 0013).</li>
 *   <li>{@code updateInfo}는 {@code HIDDEN}을 <b>푼다.</b> ADMIN의 {@code PUT}이 지워진 상품을
 *       되살리는 유일한 통로이기 때문이다. 서비스의 수정·삭제 경로가 {@code findById}로
 *       {@code HIDDEN}까지 읽는 것도 이 때문이다.</li>
 * </ul>
 *
 * <p><b>{@code Category}와 달리 {@code @OneToMany}를 둔다</b><br>
 * {@code Category}는 트리 조립에 부모 id만 있으면 돼서 컬렉션을 의도적으로 피했다. 이미지는
 * 사정이 다르다 — <b>상품 응답에 URL의 실제 값이 필요하다.</b> 목록 20건을 그냥 매핑하면 쿼리가
 * 21번 나가므로 {@code @BatchSize}로 접는다. 실제 쿼리 수는 6단계 {@code ProductRepositoryTest}가 고정한다.
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>{@code price}는 {@code BigDecimal}/{@code DECIMAL(10,2)}다. Phase 4가
 *       {@code priceAtOrder × quantity}를 합산하므로 금액에 부동소수점을 쓰지 않는다.</li>
 *   <li>{@code description}에 {@code columnDefinition}을 빼면 Hibernate가 {@code VARCHAR(255)}로
 *       잡아 {@code V3}의 {@code TEXT}와 어긋난다({@code validate} 실패). {@code @Lob}은
 *       {@code longtext}가 되어 역시 어긋난다.</li>
 *   <li>삭제는 하드 삭제가 아니다. {@link #hide()}가 상태만 바꾸고 <b>행은 남는다.</b></li>
 *   <li>재고 하한(0)은 {@link #decreaseStock(int)}이 지킨다. 가격·이름의 형식 검증은 요청 DTO가
 *       맡는다 — {@code Category}에서 이름 검증을 DTO에 둔 것과 같은 분담이다.</li>
 * </ul>
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    /**
     * 상품 이미지. <b>전시 순서대로</b> 보관한다.
     *
     * <p>{@code cascade = ALL} + {@code orphanRemoval}로 생명주기를 상품에 묶는다 —
     * 이미지는 상품 없이 존재할 이유가 없다. {@code @BatchSize}는 목록 조회의 N+1을 접기 위한 것이고,
     * 크기 100은 페이지 최대 크기(100)와 맞춘 값이다.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<ProductImage> images = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Product(Category category, String name, String description, BigDecimal price, int stockQuantity) {
        this.category = category;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        applyStockStatus();
    }

    /**
     * 상품 등록.
     *
     * <p>초기 상태를 인자로 받지 않는다 — <b>재고가 정한다.</b> 재고 0으로 등록하면
     * 곧바로 {@code SOLD_OUT}이다. "등록하면서 숨김 상태로 둔다"는 유스케이스는 계약에 없다.
     *
     * @param description 없으면 {@code null}
     */
    public static Product create(Category category, String name, String description,
                                 BigDecimal price, int stockQuantity) {
        return new Product(category, name, description, price, stockQuantity);
    }

    /**
     * 재고 차감. <b>Phase 4의 주문 생성이 호출하게 된다.</b>
     *
     * <p>모자라면 차감하지 않고 던진다 — 재고가 음수로 내려가는 경로를 만들지 않는다.
     *
     * <p>{@code HIDDEN} 상품은 상태를 유지한다. 공개 조회에서 숨겨져 있어 주문될 일이 없지만,
     * 방어적으로도 삭제 축을 건드리지 않는 편이 일관된다.
     *
     * @throws BusinessException 재고가 모자라면 P002(409). <b>이때 재고는 그대로다</b>
     */
    public void decreaseStock(int quantity) {
        if (this.stockQuantity < quantity) {
            throw new BusinessException(ProductErrorCode.OUT_OF_STOCK);
        }
        this.stockQuantity -= quantity;

        if (!isHidden()) {
            applyStockStatus();
        }
    }

    /**
     * 재고 복구. <b>Phase 4의 주문 취소·결제 실패가 호출하게 된다.</b>
     *
     * <p>⚠️ <b>{@code HIDDEN}은 풀지 않는다.</b> 이 조건이 빠지면 지워진 상품이 재고 복구만으로
     * 목록에 다시 뜬다 — soft delete가 조용히 뒤집히는 유일한 경로다(클래스 Javadoc 참고).
     * {@code HIDDEN}을 되돌리는 것은 {@link #updateInfo}뿐이다.
     */
    public void increaseStock(int quantity) {
        this.stockQuantity += quantity;

        if (!isHidden()) {
            applyStockStatus();
        }
    }

    /**
     * 상품 수정. {@code PUT /api/products/{id}}
     *
     * <p><b>부분 수정이 아니다.</b> {@code User.updateProfile}의 "null이면 변경하지 않음" 규약을
     * 쓰지 않는다 — 요청 DTO가 필수값을 이미 걸러 주고, {@code description}만 비울 수 있다.
     *
     * <p><b>{@code HIDDEN}을 푼다.</b> {@link #applyStockStatus()}를 조건 없이 호출하므로
     * 지워진 상품이 재고에 따라 {@code ON_SALE} 또는 {@code SOLD_OUT}으로 돌아온다.
     * ADMIN이 삭제를 되돌리는 통로이며, {@code increaseStock}과 갈리는 지점이다.
     */
    public void updateInfo(Category category, String name, String description,
                           BigDecimal price, int stockQuantity) {
        this.category = category;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        applyStockStatus();
    }

    /**
     * soft delete. {@code DELETE /api/products/{id}}
     *
     * <p><b>행을 지우지 않는다.</b> 주문 이력에 연결된 상품이 사라지면 안 되기 때문이다 —
     * 카테고리를 하드 삭제로 둔 것과 갈리는 지점이고, 그 근거는 카테고리가 주문 이력에
     * 매달리지 않는다는 것이었다.
     *
     * <p>재고는 건드리지 않는다. 되살릴 때 원래 재고가 그대로 있어야 한다.
     */
    public void hide() {
        this.status = ProductStatus.HIDDEN;
    }

    /**
     * 이미지 추가. {@code displayOrder}는 <b>현재 개수로 자동 부여</b>한다.
     *
     * <p>호출자가 순서를 정하지 않는 이유: 순서는 컬렉션이 소유하는 성질이라 개별 호출자가
     * 정하면 중복이나 구멍이 생긴다. 넣는 순서가 곧 전시 순서다.
     */
    public void addImage(String imageUrl) {
        this.images.add(ProductImage.of(this, imageUrl, this.images.size()));
    }

    /**
     * 이미지 전체 교체.
     *
     * <p>부분 수정(한 장만 빼기)을 제공하지 않는다 — 클라이언트가 최종 목록을 보내면
     * 그대로 맞추는 편이 순서 재계산이 필요 없어 단순하다. 빠진 행은 {@code orphanRemoval}이 지운다.
     *
     * <p>⚠️ <b>{@code this.images = new ArrayList<>(...)}로 통째 교체하지 않는다.</b>
     * Hibernate가 추적하는 컬렉션 인스턴스를 바꾸면 {@code orphanRemoval}이 동작하지 않는다.
     * 반드시 같은 인스턴스를 비우고 채운다.
     *
     * @param imageUrls 비어 있으면 이미지가 전부 사라진다
     */
    public void replaceImages(List<String> imageUrls) {
        this.images.clear();
        for (String imageUrl : imageUrls) {
            addImage(imageUrl);
        }
    }

    /** 숨김(soft delete) 상태인지. 공개 조회에서 걸러야 하는 대상이다. */
    public boolean isHidden() {
        return this.status == ProductStatus.HIDDEN;
    }

    /**
     * 이미지 목록. <b>수정할 수 없다.</b>
     *
     * <p>Lombok이 만든 getter를 덮어쓴다 — 밖에서 {@code getImages().add(...)}로 컬렉션을 건드리면
     * {@code displayOrder}가 어긋나고, Setter를 두지 않는다는 규칙도 우회된다.
     * 추가·교체는 {@link #addImage(String)}·{@link #replaceImages(List)}로만 연다.
     */
    public List<ProductImage> getImages() {
        return Collections.unmodifiableList(this.images);
    }

    /**
     * 재고로부터 {@code status}를 다시 계산한다. <b>재고 축만 다룬다.</b>
     *
     * <p>{@code HIDDEN}을 검사하지 않는 것이 핵심이다 — 이 메서드는 "재고가 이러면 상태는 이것"만
     * 알고, <b>{@code HIDDEN}을 지킬지 풀지는 호출자가 정한다.</b> 그래서
     * {@code increaseStock}/{@code decreaseStock}은 {@link #isHidden()}으로 감싸 호출하고,
     * {@code updateInfo}는 조건 없이 호출한다. 두 정책의 차이가 호출부 한 줄에 드러난다.
     */
    private void applyStockStatus() {
        this.status = (this.stockQuantity == 0) ? ProductStatus.SOLD_OUT : ProductStatus.ON_SALE;
    }
}
