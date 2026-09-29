package com.toyproject.backend.domain.product.entity;

import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.product.exception.ProductErrorCode;
import com.toyproject.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * [Phase 3 · 5단계] {@link Product}의 재고·상태 불변식 검증
 *
 * <p>재고와 {@code status}를 함께 움직이는 규칙은 전부 이 엔티티 안에 있다. 그 규칙을 지우면
 * 무엇이 깨지는지를 여기서 못박는다 — 특히 <b>{@code HIDDEN}이 재고 복구로 풀리지 않는다</b>는
 * 것은 Phase 4(주문 취소·결제 실패)가 재고를 되돌릴 때에야 증상이 나타나므로, 그때 잡으면 늦다.
 *
 * <p>Spring 컨텍스트도 DB도 필요 없다. {@code CategoryTest}와 같은 이유이며, 이 불변식이
 * 영속성과 무관하게 성립한다는 뜻이기도 하다.
 */
@DisplayName("Product")
class ProductTest {

    private static final String NAME = "기본 반팔티";
    private static final String DESCRIPTION = "면 100%";
    private static final BigDecimal PRICE = new BigDecimal("19900.00");

    private static Category category() {
        return Category.child(Category.root("상의", 1), "티셔츠", 1);
    }

    private static Product product(int stock) {
        return Product.create(category(), NAME, DESCRIPTION, PRICE, stock);
    }

    /** 숨김 상태 상품. {@code hide()}가 유일한 진입 경로다. */
    private static Product hiddenProduct(int stock) {
        Product product = product(stock);
        product.hide();
        return product;
    }

    @Nested
    @DisplayName("등록하면 재고가 상태를 정한다")
    class Creation {

        @Test
        @DisplayName("재고가 있으면 ON_SALE")
        void withStockIsOnSale() {
            Product product = product(10);

            assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
            assertThat(product.getStockQuantity()).isEqualTo(10);
            assertThat(product.getName()).isEqualTo(NAME);
            assertThat(product.getPrice()).isEqualByComparingTo(PRICE);
            assertThat(product.isHidden()).isFalse();
        }

        @Test
        @DisplayName("재고 0으로 등록하면 곧바로 SOLD_OUT")
        void withoutStockIsSoldOut() {
            Product product = product(0);

            assertThat(product.getStatus())
                    .as("초기 상태를 인자로 받지 않는다 — 재고가 정한다")
                    .isEqualTo(ProductStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("설명은 비워 둘 수 있다")
        void descriptionIsOptional() {
            Product product = Product.create(category(), NAME, null, PRICE, 1);

            assertThat(product.getDescription()).isNull();
        }
    }

    @Nested
    @DisplayName("재고를 깎으면 상태가 따라온다")
    class DecreaseStock {

        @Test
        @DisplayName("음수를 넘기면 거절한다 — 차감이 증가로 뒤집히면 안 된다")
        void rejectsNegativeQuantity() {
            Product product = product(10);

            assertThatThrownBy(() -> product.decreaseStock(-5))
                    .as("허용하면 재고가 15가 되고 P002 검사도 우회된다")
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(product.getStockQuantity())
                    .as("거절됐으니 재고는 그대로다")
                    .isEqualTo(10);
        }

        @Test
        @DisplayName("0을 넘기면 거절한다 — 아무것도 하지 않는 호출은 버그다")
        void rejectsZeroQuantity() {
            Product product = product(10);

            assertThatThrownBy(() -> product.decreaseStock(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("일부만 깎으면 ON_SALE을 유지한다")
        void partialDecreaseKeepsOnSale() {
            Product product = product(10);

            product.decreaseStock(3);

            assertThat(product.getStockQuantity()).isEqualTo(7);
            assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        }

        @Test
        @DisplayName("0이 되면 SOLD_OUT으로 넘어간다")
        void decreaseToZeroBecomesSoldOut() {
            Product product = product(3);

            product.decreaseStock(3);

            assertThat(product.getStockQuantity()).isZero();
            assertThat(product.getStatus())
                    .as("호출자가 status를 만지지 않아도 품절이 된다")
                    .isEqualTo(ProductStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("재고보다 많이 깎으면 P002이고 재고는 그대로다")
        void decreaseBeyondStockRejected() {
            Product product = product(2);

            assertThatThrownBy(() -> product.decreaseStock(3))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.OUT_OF_STOCK);

            assertThat(product.getStockQuantity())
                    .as("거절된 차감은 재고를 건드리지 않는다 — 음수로 내려가는 경로를 만들지 않는다")
                    .isEqualTo(2);
            assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        }
    }

    @Nested
    @DisplayName("재고를 채우면 품절만 풀린다")
    class IncreaseStock {

        @Test
        @DisplayName("음수·0을 넘기면 거절한다")
        void rejectsNonPositiveQuantity() {
            Product product = product(10);

            assertThatThrownBy(() -> product.increaseStock(-5))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> product.increaseStock(0))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(product.getStockQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("SOLD_OUT은 ON_SALE로 돌아온다")
        void soldOutReturnsToOnSale() {
            Product product = product(0);

            product.increaseStock(5);

            assertThat(product.getStockQuantity()).isEqualTo(5);
            assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        }

        @Test
        @DisplayName("ON_SALE은 그대로 ON_SALE이다")
        void onSaleStaysOnSale() {
            Product product = product(1);

            product.increaseStock(5);

            assertThat(product.getStockQuantity()).isEqualTo(6);
            assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        }
    }

    @Nested
    @DisplayName("숨김은 재고로 풀리지 않는다")
    class HiddenIsNotUndoneByStock {

        @Test
        @DisplayName("hide()는 ON_SALE을 HIDDEN으로 바꾸고 재고는 남긴다")
        void hideKeepsStock() {
            Product product = product(10);

            product.hide();

            assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
            assertThat(product.isHidden()).isTrue();
            assertThat(product.getStockQuantity())
                    .as("되살릴 때 원래 재고가 그대로 있어야 한다")
                    .isEqualTo(10);
        }

        @Test
        @DisplayName("SOLD_OUT에서도 숨길 수 있다")
        void hideFromSoldOut() {
            Product product = product(0);

            product.hide();

            assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        }

        @Test
        @DisplayName("⚠️ 숨긴 상품에 재고를 채워도 HIDDEN이다")
        void increaseStockDoesNotRevive() {
            Product product = hiddenProduct(0);

            product.increaseStock(10);

            assertThat(product.getStockQuantity()).isEqualTo(10);
            assertThat(product.getStatus())
                    .as("여기서 ON_SALE이 되면 Phase 4의 주문 취소가 삭제된 상품을 목록에 되살린다")
                    .isEqualTo(ProductStatus.HIDDEN);
        }

        @Test
        @DisplayName("숨긴 상품은 재고를 깎아도 HIDDEN이다")
        void decreaseStockKeepsHidden() {
            Product product = hiddenProduct(5);

            product.decreaseStock(5);

            assertThat(product.getStockQuantity()).isZero();
            assertThat(product.getStatus())
                    .as("재고 축은 삭제 축을 건드리지 않는다")
                    .isEqualTo(ProductStatus.HIDDEN);
        }
    }

    @Nested
    @DisplayName("수정은 숨김을 푼다")
    class UpdateInfo {

        @Test
        @DisplayName("HIDDEN 상품을 수정하면 재고에 따라 되살아난다")
        void updateRevivesHidden() {
            Product product = hiddenProduct(0);

            product.updateInfo(category(), "새 이름", "새 설명", new BigDecimal("29900.00"), 7);

            assertThat(product.getStatus())
                    .as("ADMIN의 PUT이 삭제를 되돌리는 유일한 통로다")
                    .isEqualTo(ProductStatus.ON_SALE);
            assertThat(product.getName()).isEqualTo("새 이름");
            assertThat(product.getStockQuantity()).isEqualTo(7);
        }

        @Test
        @DisplayName("재고 0으로 수정하면 SOLD_OUT이다")
        void updateToZeroStockIsSoldOut() {
            Product product = product(10);

            product.updateInfo(category(), NAME, DESCRIPTION, PRICE, 0);

            assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("설명을 null로 비울 수 있다")
        void updateCanClearDescription() {
            Product product = product(1);

            product.updateInfo(category(), NAME, null, PRICE, 1);

            assertThat(product.getDescription())
                    .as("부분 수정이 아니라서 null이 '변경하지 않음'을 뜻하지 않는다")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("이미지 순서는 상품이 부여한다")
    class Images {

        @Test
        @DisplayName("추가한 순서대로 displayOrder가 0부터 붙는다")
        void addImageAssignsOrder() {
            Product product = product(1);

            product.addImage("https://cdn.example.com/a.jpg");
            product.addImage("https://cdn.example.com/b.jpg");

            assertThat(product.getImages())
                    .extracting(ProductImage::getImageUrl, ProductImage::getDisplayOrder)
                    .containsExactly(
                            tuple("https://cdn.example.com/a.jpg", 0),
                            tuple("https://cdn.example.com/b.jpg", 1));
            assertThat(product.getImages().get(0).getProduct()).isSameAs(product);
        }

        @Test
        @DisplayName("교체하면 순서가 0부터 다시 매겨진다")
        void replaceImagesRenumbers() {
            Product product = product(1);
            product.addImage("https://cdn.example.com/a.jpg");
            product.addImage("https://cdn.example.com/b.jpg");

            product.replaceImages(List.of("https://cdn.example.com/c.jpg"));

            assertThat(product.getImages())
                    .extracting(ProductImage::getImageUrl, ProductImage::getDisplayOrder)
                    .containsExactly(
                            tuple("https://cdn.example.com/c.jpg", 0));
        }

        @Test
        @DisplayName("빈 목록으로 교체하면 이미지가 사라진다")
        void replaceWithEmptyClears() {
            Product product = product(1);
            product.addImage("https://cdn.example.com/a.jpg");

            product.replaceImages(List.of());

            assertThat(product.getImages()).isEmpty();
        }

        @Test
        @DisplayName("반환된 목록은 밖에서 고칠 수 없다")
        void imagesAreUnmodifiable() {
            Product product = product(1);
            product.addImage("https://cdn.example.com/a.jpg");

            assertThatThrownBy(() -> product.getImages().clear())
                    .as("컬렉션을 직접 건드리면 displayOrder가 어긋난다")
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
