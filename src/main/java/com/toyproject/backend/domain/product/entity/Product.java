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
 * [Phase 3 · 5단계] 상품 엔티티.
 *
 * <p>Setter를 두지 않고, <b>재고를 바꾸는 메서드가 {@code status}까지 확정한다</b> —
 * 판정이 {@link #applyStockStatus()} 하나에 모여 있어 재고 0인 {@code ON_SALE}이 생길 수 없다.
 *
 * <p>⚠️ {@link #increaseStock(int)}은 {@code HIDDEN}을 <b>풀지 않고</b>
 * {@link #updateInfo}는 <b>푼다.</b> 의도된 비대칭이며 이 클래스에서 가장 틀리기 쉬운 지점이다
 * (ADR 0013).
 *
 * <p>삭제는 {@link #hide()}로 상태만 바꾸고 행은 남긴다. 계약은 {@code docs/domains/catalog.md}.
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

    /** {@code columnDefinition}을 빼면 {@code VARCHAR(255)}가 되어 V3의 {@code TEXT}와 어긋난다. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Phase 4가 금액을 합산하므로 부동소수점을 쓰지 않는다. */
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
     * 상품 이미지. {@code @BatchSize}가 목록 조회의 N+1을 접는다 — 크기는 페이지 상한(100)과 맞췄다.
     * {@code Category}와 달리 컬렉션을 두는 이유는 응답에 URL의 실제 값이 필요하기 때문이다.
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

    /** 상품 등록. 초기 상태는 재고가 정한다 — 재고 0이면 곧바로 {@code SOLD_OUT}이다. */
    public static Product create(Category category, String name, String description,
                                 BigDecimal price, int stockQuantity) {
        return new Product(category, name, description, price, stockQuantity);
    }

    /**
     * 재고 차감. {@code HIDDEN}이면 상태를 건드리지 않는다.
     *
     * @throws BusinessException        재고가 모자라면 P002(409). 이때 재고는 그대로다
     * @throws IllegalArgumentException {@code quantity}가 1 미만이면
     */
    public void decreaseStock(int quantity) {
        requirePositive(quantity);

        if (this.stockQuantity < quantity) {
            throw new BusinessException(ProductErrorCode.OUT_OF_STOCK);
        }
        this.stockQuantity -= quantity;

        if (!isHidden()) {
            applyStockStatus();
        }
    }

    /**
     * 재고 복구. {@code HIDDEN}은 풀지 않는다 — 풀면 Phase 4의 주문 취소가
     * 삭제된 상품을 목록에 되살린다(ADR 0013).
     *
     * @throws IllegalArgumentException {@code quantity}가 1 미만이면
     */
    public void increaseStock(int quantity) {
        requirePositive(quantity);

        this.stockQuantity += quantity;

        if (!isHidden()) {
            applyStockStatus();
        }
    }

    /**
     * 상품 수정. 부분 수정이 아니라 전체 교체다.
     *
     * <p>{@code HIDDEN}을 <b>푼다</b> — ADMIN이 삭제를 되돌리는 통로이며
     * {@link #increaseStock(int)}과 갈리는 지점이다.
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

    /** soft delete. 주문 이력이 매달리므로 행을 지우지 않고, 재고도 건드리지 않는다. */
    public void hide() {
        this.status = ProductStatus.HIDDEN;
    }

    /** 이미지 추가. {@code displayOrder}는 현재 개수로 자동 부여한다. */
    public void addImage(String imageUrl) {
        this.images.add(ProductImage.of(this, imageUrl, this.images.size()));
    }

    /**
     * 이미지 전체 교체. 빠진 행은 {@code orphanRemoval}이 지운다.
     *
     * <p>컬렉션 인스턴스를 새로 대입하지 않는다 — Hibernate가 추적하던 인스턴스가 바뀌면
     * {@code orphanRemoval}이 동작하지 않는다.
     */
    public void replaceImages(List<String> imageUrls) {
        this.images.clear();
        for (String imageUrl : imageUrls) {
            addImage(imageUrl);
        }
    }

    public boolean isHidden() {
        return this.status == ProductStatus.HIDDEN;
    }

    /** 밖에서 컬렉션을 고치면 {@code displayOrder}가 어긋나므로 불변 목록을 돌려준다. */
    public List<ProductImage> getImages() {
        return Collections.unmodifiableList(this.images);
    }

    /**
     * 재고 증감은 양수만 받는다. 음수를 허용하면 차감이 증가가 되고 P002 검사도 우회된다 —
     * 클라이언트 입력이 아니라 호출자 버그이므로 {@code BusinessException}이 아니다.
     */
    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("재고 증감 수량은 1 이상이어야 합니다: " + quantity);
        }
    }

    /**
     * 재고로부터 {@code status}를 다시 계산한다. <b>{@code HIDDEN}을 검사하지 않는다</b> —
     * 숨김을 지킬지 풀지는 호출부가 정하고, 그 차이가 호출부 한 줄에 드러난다.
     */
    private void applyStockStatus() {
        this.status = (this.stockQuantity == 0) ? ProductStatus.SOLD_OUT : ProductStatus.ON_SALE;
    }
}
