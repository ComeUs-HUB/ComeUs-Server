package com.toyproject.backend.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [Phase 3 · 5단계] 상품 이미지. URL과 순서만 담고 파일 바이트는 다루지 않는다(ADR 0012).
 *
 * <p>생성 경로가 {@link Product}뿐이다 — 정적 팩터리가 package-private이라 부모 없는 이미지를
 * 만들 수 없고, {@code displayOrder}도 부모가 부여한다. 수정은
 * {@link Product#replaceImages(java.util.List)}로 전부 교체한다.
 */
@Entity
@Table(name = "product_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** S3 전환 시 버킷·리전·키가 붙어 길어지므로 500자로 둔다. */
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private ProductImage(Product product, String imageUrl, int displayOrder) {
        this.product = product;
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
    }

    /** {@link Product}만 호출한다. */
    static ProductImage of(Product product, String imageUrl, int displayOrder) {
        return new ProductImage(product, imageUrl, displayOrder);
    }
}
