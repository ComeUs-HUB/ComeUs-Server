package com.toyproject.backend.domain.product.service;

import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.exception.CategoryErrorCode;
import com.toyproject.backend.domain.category.service.CategoryService;
import com.toyproject.backend.domain.product.dto.ProductCreateRequest;
import com.toyproject.backend.domain.product.dto.ProductDetailResponse;
import com.toyproject.backend.domain.product.dto.ProductListResponse;
import com.toyproject.backend.domain.product.dto.ProductSearchRequest;
import com.toyproject.backend.domain.product.dto.ProductUpdateRequest;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductImage;
import com.toyproject.backend.domain.product.entity.ProductStatus;
import com.toyproject.backend.domain.product.exception.ProductErrorCode;
import com.toyproject.backend.domain.product.repository.ProductRepository;
import com.toyproject.backend.global.exception.BusinessException;
import com.toyproject.backend.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * [Phase 3 · 7단계] {@link ProductService} 검증.
 *
 * <p>Spring 컨텍스트 없는 순수 단위 테스트다. 이 단계의 판단은 <b>펼치기 위임, HIDDEN 거절,
 * DTO 변환</b> 셋이고 전부 DB 없이 드러난다. 실제 SQL은 {@code ProductRepositoryTest}가 덮는다.
 *
 * <p>{@code id}를 {@link ReflectionTestUtils}로 심는 이유는 DTO 변환이 {@code getId()}를 쓰기
 * 때문이다 — 저장을 거치지 않아 JPA가 채워 주지 않는다({@code CategoryServiceTest}와 같다).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService")
class ProductServiceTest {

    private static final ProductSearchRequest NO_FILTER =
            new ProductSearchRequest(null, null, null, null);

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private ProductService productService;

    private static Category category(long id, String name) {
        Category category = Category.root(name, 1);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private static Product product(long id, String name, String price, String... imageUrls) {
        Product product = Product.create(category(9L, "티셔츠"), name, name + " 설명",
                new BigDecimal(price), 10);
        ReflectionTestUtils.setField(product, "id", id);
        for (String imageUrl : imageUrls) {
            product.addImage(imageUrl);
        }
        return product;
    }

    private static ProductCreateRequest createRequest(long categoryId, String name,
                                                     String price, int stock, String... imageUrls) {
        return new ProductCreateRequest(categoryId, name, name + " 설명",
                new BigDecimal(price), stock, List.of(imageUrls));
    }

    private static ProductUpdateRequest updateRequest(long categoryId, String name,
                                                     String price, int stock, String... imageUrls) {
        return new ProductUpdateRequest(categoryId, name, name + " 설명",
                new BigDecimal(price), stock, List.of(imageUrls));
    }

    /** {@code save}가 인자를 그대로 돌려주게 한다 — 응답 DTO가 저장된 엔티티를 읽기 때문이다. */
    private void givenSaveReturnsArgument() {
        given(productRepository.save(any(Product.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenPage(Page<Product> page) {
        given(productRepository.findAll(
                ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class)))
                .willReturn(page);
    }

    @Nested
    @DisplayName("목록 조회 — 카테고리 펼치기를 category 도메인에 맡긴다")
    class ListCategoryExpansion {

        @Test
        @DisplayName("categoryId가 있으면 CategoryService에 펼치기를 위임한다")
        void delegatesExpansion() {
            given(categoryService.collectSelfAndChildIds(1L)).willReturn(List.of(1L, 3L, 4L));
            givenPage(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            productService.getList(new ProductSearchRequest(1L, null, null, null),
                    PageRequest.of(0, 20));

            then(categoryService).should().collectSelfAndChildIds(1L);
        }

        @Test
        @DisplayName("categoryId가 없으면 CategoryService를 아예 부르지 않는다")
        void skipsExpansionWithoutCategoryId() {
            givenPage(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            productService.getList(NO_FILTER, PageRequest.of(0, 20));

            // 카테고리 필터를 안 쓰는 요청에서 쿼리 한 번을 아끼는 분기다
            then(categoryService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("없는 카테고리면 CA001이 그대로 전파되고 상품을 조회하지 않는다")
        void propagatesCategoryNotFound() {
            given(categoryService.collectSelfAndChildIds(99L))
                    .willThrow(new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));

            assertThatThrownBy(() -> productService.getList(
                    new ProductSearchRequest(99L, null, null, null), PageRequest.of(0, 20)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_NOT_FOUND);

            // 빈 목록으로 뭉개지 않는다 — 없는 것을 지목한 요청은 404다
            then(productRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("목록 조회 — 페이지를 응답으로 바꾼다")
    class ListResponseMapping {

        @Test
        @DisplayName("페이지 정보가 PageResponse에 그대로 보존된다")
        void preservesPageMetadata() {
            Page<Product> page = new PageImpl<>(
                    List.of(product(1L, "기본 반팔티", "19900"), product(2L, "슬랙스", "39900")),
                    PageRequest.of(0, 2), 5);
            givenPage(page);

            PageResponse<ProductListResponse> response =
                    productService.getList(NO_FILTER, PageRequest.of(0, 2));

            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getTotalElements()).isEqualTo(5);
            assertThat(response.getTotalPages()).isEqualTo(3);
            assertThat(response.getCurrentPage()).isZero();
            assertThat(response.getPageSize()).isEqualTo(2);
            assertThat(response.isHasNext()).isTrue();
            assertThat(response.isHasPrevious()).isFalse();
        }

        @Test
        @DisplayName("결과가 없어도 실패가 아니다 — 빈 content와 0건")
        void emptyPageIsNotFailure() {
            givenPage(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            PageResponse<ProductListResponse> response =
                    productService.getList(NO_FILTER, PageRequest.of(0, 20));

            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isZero();
            assertThat(response.isHasNext()).isFalse();
        }

        @Test
        @DisplayName("대표 이미지는 displayOrder가 가장 작은 한 장이다")
        void thumbnailIsFirstImage() {
            Product product = product(1L, "기본 반팔티", "19900",
                    "https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg");
            givenPage(new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1));

            PageResponse<ProductListResponse> response =
                    productService.getList(NO_FILTER, PageRequest.of(0, 20));

            assertThat(response.getContent().get(0).thumbnailUrl())
                    .as("addImage가 0부터 부여하므로 먼저 넣은 것이 대표다")
                    .isEqualTo("https://cdn.example.com/a.jpg");
        }

        @Test
        @DisplayName("이미지가 없으면 thumbnailUrl은 null이다")
        void thumbnailIsNullWithoutImages() {
            givenPage(new PageImpl<>(List.of(product(1L, "기본 반팔티", "19900")),
                    PageRequest.of(0, 20), 1));

            PageResponse<ProductListResponse> response =
                    productService.getList(NO_FILTER, PageRequest.of(0, 20));

            assertThat(response.getContent().get(0).thumbnailUrl())
                    .as("기본 이미지를 무엇으로 할지는 화면의 결정이라 서버가 정하지 않는다")
                    .isNull();
        }

        @Test
        @DisplayName("목록에는 description과 재고를 담지 않는다")
        void listOmitsDetailFields() {
            givenPage(new PageImpl<>(List.of(product(1L, "기본 반팔티", "19900")),
                    PageRequest.of(0, 20), 1));

            PageResponse<ProductListResponse> response =
                    productService.getList(NO_FILTER, PageRequest.of(0, 20));

            assertThat(response.getContent().get(0))
                    .as("목록 DTO에 그 필드가 없어야 바디가 커지지 않는다")
                    .hasOnlyFields("id", "name", "price", "status", "categoryId", "thumbnailUrl");
        }
    }

    @Nested
    @DisplayName("상세 조회")
    class Detail {

        @Test
        @DisplayName("보이는 상품이면 상세 응답으로 바꿔 돌려준다")
        void returnsDetail() {
            Product product = product(1L, "기본 반팔티", "19900", "https://cdn.example.com/a.jpg");
            given(productRepository.findByIdAndStatusNot(1L, ProductStatus.HIDDEN))
                    .willReturn(Optional.of(product));

            ProductDetailResponse response = productService.getDetail(1L);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("기본 반팔티");
            assertThat(response.description()).isEqualTo("기본 반팔티 설명");
            assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE);
            assertThat(response.categoryId()).isEqualTo(9L);
        }

        @Test
        @DisplayName("품절은 재고 수량이 아니라 status로만 드러난다")
        void soldOutShowsOnlyInStatus() {
            Product soldOut = Product.create(category(9L, "티셔츠"), "품절 반팔티", "설명",
                    new BigDecimal("19900"), 0);
            ReflectionTestUtils.setField(soldOut, "id", 2L);
            given(productRepository.findByIdAndStatusNot(2L, ProductStatus.HIDDEN))
                    .willReturn(Optional.of(soldOut));

            ProductDetailResponse response = productService.getDetail(2L);

            assertThat(response.status())
                    .as("공개 응답에 재고 수량이 없으므로 구매 가능 여부는 이 필드가 유일한 근거다")
                    .isEqualTo(ProductStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("없거나 숨긴 상품이면 P001 — 둘을 구분하지 않는다")
        void missingOrHiddenIsP001() {
            given(productRepository.findByIdAndStatusNot(99L, ProductStatus.HIDDEN))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getDetail(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .as("구분해서 알려주면 id를 훑어 삭제된 상품의 존재를 알아낼 수 있다")
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        @Test
        @DisplayName("HIDDEN을 거르는 것은 서비스가 아니라 조회 메서드다")
        void filterLivesInQueryMethod() {
            given(productRepository.findByIdAndStatusNot(1L, ProductStatus.HIDDEN))
                    .willReturn(Optional.of(product(1L, "기본 반팔티", "19900")));

            productService.getDetail(1L);

            then(productRepository).should().findByIdAndStatusNot(1L, ProductStatus.HIDDEN);
            // findById를 쓰면 숨긴 상품이 새어 나간다 — 그 메서드는 수정·삭제 전용이다
            then(productRepository).should(never()).findById(any());
        }

        @Test
        @DisplayName("이미지는 전시 순서대로 편다")
        void imagesAreOrdered() {
            Product product = product(1L, "기본 반팔티", "19900",
                    "https://cdn.example.com/a.jpg",
                    "https://cdn.example.com/b.jpg",
                    "https://cdn.example.com/c.jpg");
            given(productRepository.findByIdAndStatusNot(1L, ProductStatus.HIDDEN))
                    .willReturn(Optional.of(product));

            ProductDetailResponse response = productService.getDetail(1L);

            assertThat(response.imageUrls()).containsExactly(
                    "https://cdn.example.com/a.jpg",
                    "https://cdn.example.com/b.jpg",
                    "https://cdn.example.com/c.jpg");
        }

        @Test
        @DisplayName("이미지가 없으면 null이 아니라 빈 배열이다")
        void emptyImagesIsEmptyList() {
            given(productRepository.findByIdAndStatusNot(1L, ProductStatus.HIDDEN))
                    .willReturn(Optional.of(product(1L, "기본 반팔티", "19900")));

            assertThat(productService.getDetail(1L).imageUrls())
                    .as("클라이언트가 null 검사 없이 순회할 수 있어야 한다")
                    .isNotNull()
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("등록")
    class Create {

        @Test
        @DisplayName("카테고리는 CategoryService를 경유해 얻는다")
        void delegatesCategoryLookup() {
            given(categoryService.getCategory(9L)).willReturn(category(9L, "티셔츠"));
            givenSaveReturnsArgument();

            productService.create(createRequest(9L, "기본 반팔티", "19900", 10));

            // CategoryRepository를 직접 주입하지 않는다는 규칙이 여기서 지켜지는지 본다(ADR 0014).
            then(categoryService).should().getCategory(9L);
        }

        @Test
        @DisplayName("없는 카테고리면 CA001이 그대로 전파되고 저장하지 않는다")
        void absentCategoryIsCa001() {
            given(categoryService.getCategory(99L))
                    .willThrow(new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));

            assertThatThrownBy(() -> productService.create(createRequest(99L, "기본 반팔티", "19900", 10)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_NOT_FOUND);

            then(productRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("재고 0으로 등록하면 SOLD_OUT으로 저장된다")
        void zeroStockStartsSoldOut() {
            given(categoryService.getCategory(9L)).willReturn(category(9L, "티셔츠"));
            givenSaveReturnsArgument();

            productService.create(createRequest(9L, "오버핏 반팔티", "29900", 0));

            assertThat(savedProduct().getStatus())
                    .as("상태는 요청이 정하지 못한다 — 재고가 정한다")
                    .isEqualTo(ProductStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("이미지는 보낸 순서대로 붙는다")
        void imagesKeepRequestOrder() {
            given(categoryService.getCategory(9L)).willReturn(category(9L, "티셔츠"));
            givenSaveReturnsArgument();

            productService.create(createRequest(9L, "기본 반팔티", "19900", 10,
                    "https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg"));

            assertThat(savedProduct().getImages())
                    .extracting(ProductImage::getImageUrl)
                    .containsExactly("https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg");
        }

        @Test
        @DisplayName("이미지를 보내지 않아도 등록된다")
        void imagesAreOptional() {
            given(categoryService.getCategory(9L)).willReturn(category(9L, "티셔츠"));
            givenSaveReturnsArgument();

            ProductDetailResponse response = productService.create(
                    createRequest(9L, "기본 반팔티", "19900", 10));

            assertThat(response.imageUrls()).isEmpty();
        }

        private Product savedProduct() {
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            then(productRepository).should().save(captor.capture());
            return captor.getValue();
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("숨긴 상품을 수정하면 HIDDEN이 풀린다 — 되살리는 통로다")
        void updateUnhides() {
            Product hidden = product(1L, "단종 티셔츠", "9900");
            hidden.hide();
            given(productRepository.findById(1L)).willReturn(Optional.of(hidden));
            given(categoryService.getCategory(9L)).willReturn(category(9L, "티셔츠"));

            productService.update(1L, updateRequest(9L, "부활 티셔츠", "19900", 10));

            assertThat(hidden.getStatus())
                    .as("increaseStock과 갈리는 지점이다 — 그쪽은 HIDDEN을 풀지 않는다(ADR 0013)")
                    .isEqualTo(ProductStatus.ON_SALE);
        }

        @Test
        @DisplayName("HIDDEN까지 읽어야 하므로 findById를 쓴다")
        void readsHiddenRowsToo() {
            given(productRepository.findById(1L))
                    .willReturn(Optional.of(product(1L, "기본 반팔티", "19900")));
            given(categoryService.getCategory(9L)).willReturn(category(9L, "티셔츠"));

            productService.update(1L, updateRequest(9L, "기본 반팔티", "19900", 10));

            then(productRepository).should().findById(1L);
            // 공개 조회 메서드를 쓰면 숨긴 상품을 되살릴 수 없다.
            then(productRepository).should(never()).findByIdAndStatusNot(any(), any());
        }

        @Test
        @DisplayName("없는 상품이면 P001")
        void absentProductIsP001() {
            given(productRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.update(99L, updateRequest(9L, "x", "19900", 10)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        @Test
        @DisplayName("이미지는 통째로 교체된다 — 빠진 것은 사라진다")
        void imagesAreReplacedWholesale() {
            Product product = product(1L, "기본 반팔티", "19900",
                    "https://cdn.example.com/old-1.jpg", "https://cdn.example.com/old-2.jpg");
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(categoryService.getCategory(9L)).willReturn(category(9L, "티셔츠"));

            ProductDetailResponse response = productService.update(1L,
                    updateRequest(9L, "기본 반팔티", "19900", 10, "https://cdn.example.com/new.jpg"));

            assertThat(response.imageUrls()).containsExactly("https://cdn.example.com/new.jpg");
        }

        @Test
        @DisplayName("save를 다시 부르지 않는다 — 더티 체킹으로 반영된다")
        void reliesOnDirtyChecking() {
            given(productRepository.findById(1L))
                    .willReturn(Optional.of(product(1L, "기본 반팔티", "19900")));
            given(categoryService.getCategory(9L)).willReturn(category(9L, "티셔츠"));

            productService.update(1L, updateRequest(9L, "바뀐 이름", "29900", 5));

            then(productRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("삭제 — soft delete")
    class Delete {

        @Test
        @DisplayName("행을 지우지 않고 HIDDEN으로 바꾼다")
        void hidesInsteadOfDeleting() {
            Product product = product(1L, "기본 반팔티", "19900");
            given(productRepository.findById(1L)).willReturn(Optional.of(product));

            productService.delete(1L);

            assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
            // 행을 지우면 주문 이력이 깨진다 — 상속받은 delete는 쓰지 않는다.
            // delete(Specification) 오버로드가 JpaSpecificationExecutor에 있어 타입을 명시한다.
            then(productRepository).should(never()).delete(any(Product.class));
            then(productRepository).should(never()).deleteById(any());
        }

        @Test
        @DisplayName("재고는 건드리지 않는다 — 숨김과 재고는 다른 축이다")
        void keepsStockUntouched() {
            Product product = product(1L, "기본 반팔티", "19900");
            given(productRepository.findById(1L)).willReturn(Optional.of(product));

            productService.delete(1L);

            assertThat(product.getStockQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("없는 상품이면 P001")
        void absentProductIsP001() {
            given(productRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.delete(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 숨긴 상품을 또 삭제해도 HIDDEN 그대로다")
        void deletingHiddenIsIdempotent() {
            Product hidden = product(1L, "단종 티셔츠", "9900");
            hidden.hide();
            given(productRepository.findById(1L)).willReturn(Optional.of(hidden));

            productService.delete(1L);

            assertThat(hidden.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        }
    }
}
