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
 * [Phase 3 · 5단계] 상품 이미지 엔티티
 *
 * <p><b>역할</b><br>
 * 상품 이미지의 <b>URL과 순서</b>만 담는다. 파일 바이트는 다루지 않는다
 * ([ADR 0012](docs/decisions/0012-image-urls-in-separate-table.md)).
 *
 * <p><b>설계</b>
 * <ul>
 *   <li><b>생성 경로가 {@link Product}뿐이다.</b> 정적 팩터리를 package-private으로 두어
 *       product 패키지 밖에서는 부모 없는 이미지를 만들 수 없다. {@code displayOrder}를
 *       호출자가 정하지 않는 것도 같은 이유다 — 순서는 컬렉션이 소유하는 성질이라
 *       개별 이미지가 스스로 정하면 중복·구멍이 생긴다.</li>
 *   <li>Setter를 두지 않는다. 수정은 {@code Product.replaceImages()}로 <b>전부 교체</b>한다.
 *       {@code orphanRemoval = true}가 빠진 행을 지운다.</li>
 *   <li>{@code createdAt}을 두지 않는다. 계약에 없고, 이미지는 부모의 생명주기에 완전히 묶여 있어
 *       독립된 시각을 추적할 대상이 아니다.</li>
 * </ul>
 *
 * <p><b>{@code imageUrl}이 500자인 이유</b><br>
 * 로컬 스토리지에서는 짧지만 S3로 옮기면 버킷·리전·키가 붙어 길어진다. URL만 저장하므로
 * 전환에 스키마 변경이 없어야 한다는 것이 ADR 0012의 전제이고, 길이는 그 전제의 일부다.
 */
@Entity
@Table(name = "product_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소유 상품. <b>연관관계의 주인</b>이다.
     *
     * <p>LAZY로 두지만 실제로 초기화될 일은 거의 없다 — 이미지는 상품을 통해서만 읽히므로
     * 부모는 이미 영속성 컨텍스트에 있다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /** 전시 순서. {@code Product}가 0부터 차례로 부여한다. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private ProductImage(Product product, String imageUrl, int displayOrder) {
        this.product = product;
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
    }

    /**
     * 이미지 생성. <b>{@link Product}만 호출한다</b>(package-private).
     *
     * @param displayOrder 부모가 부여한 순서. 호출자가 직접 정하지 않는다
     */
    static ProductImage of(Product product, String imageUrl, int displayOrder) {
        return new ProductImage(product, imageUrl, displayOrder);
    }
}
