package com.toyproject.backend.domain.product.repository;

import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
import com.toyproject.backend.domain.product.dto.ProductSearchCondition;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductStatus;
import com.toyproject.backend.global.config.JpaConfig;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Phase 3 · 6단계] {@link ProductRepository}와 {@link ProductSpecifications} 검증
 *
 * <p><b>왜 여기서만 확인되는가</b><br>
 * {@code Specification}은 <b>런타임에 조립</b>된다. {@code root.get("pricee")} 같은 오타도,
 * 조각을 빠뜨린 조립도 컴파일에서 걸리지 않는다. ADR 0011이 "필터 조합 테스트로 덮는다"고
 * 적어 둔 지점이 여기이며, <b>실제 SQL이 나가야만 드러나는 것들</b>을 고정한다.
 *
 * <p><b>애노테이션 근거</b>는 {@code CategoryRepositoryTest}와 같다 —
 * {@code @Import(JpaConfig.class)}가 없으면 auditing이 꺼져 {@code createdAt}(NOT NULL)이
 * 채워지지 않고, {@code @AutoConfigureTestDatabase(replace = NONE)}가 없으면
 * {@code MODE=MySQL} 설정이 무시된다.
 *
 * <p><b>고정 데이터</b> — 보이는 상품 4건, 숨긴 상품 2건.
 * <pre>
 * 상의(대)                      하의(대)    아우터(대)      가방(대)
 *  ├ 티셔츠(소)                  슬랙스      단종 코트       (상품 없음)
 *  │   ├ 기본 반팔티  19,900     39,900      99,000 HIDDEN
 *  │   ├ 오버핏 반팔티 29,900 (재고0 → SOLD_OUT)
 *  │   └ 단종 티셔츠   9,900 HIDDEN
 *  └ 셔츠(소)
 *      └ Oxford 셔츠  49,900
 * </pre>
 */
@DataJpaTest
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ProductRepository · ProductSpecifications")
class ProductRepositoryTest {

    private static final ProductSearchCondition NO_CONDITION =
            new ProductSearchCondition(null, null, null, null);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager em;

    private Category tops;
    private Category tshirt;
    private Category shirt;
    private Category bottoms;
    private Category outer;
    private Category bag;

    private Product basicTee;
    private Product overfitTee;
    private Product oxfordShirt;
    private Product slacks;
    private Product hiddenTee;
    private Product hiddenCoat;

    // 재고 초기화
    @BeforeEach
    void setUp() {
        tops = categoryRepository.save(Category.root("상의", 1));
        bottoms = categoryRepository.save(Category.root("하의", 2));
        outer = categoryRepository.save(Category.root("아우터", 3));
        bag = categoryRepository.save(Category.root("가방", 4));
        tshirt = categoryRepository.save(Category.child(tops, "티셔츠", 1));
        shirt = categoryRepository.save(Category.child(tops, "셔츠", 2));

        basicTee = saveProduct(tshirt, "기본 반팔티", "19900", 10);
        overfitTee = saveProduct(tshirt, "오버핏 반팔티", "29900", 0);
        oxfordShirt = saveProduct(shirt, "Oxford 셔츠", "49900", 5);
        slacks = saveProduct(bottoms, "슬랙스", "39900", 3);
        hiddenTee = saveHiddenProduct(tshirt, "단종 티셔츠", "9900", 1);
        hiddenCoat = saveHiddenProduct(outer, "단종 코트", "99000", 0);

        em.flush();
        em.clear();
    }

    // 상품 기본 정보 입력 메서드
    private Product saveProduct(Category category, String name, String price, int stock) {
        Product product = Product.create(category, name, name + " 설명", new BigDecimal(price), stock);
        product.addImage("https://cdn.example.com/" + name + "-1.jpg");
        product.addImage("https://cdn.example.com/" + name + "-2.jpg");
        return productRepository.save(product);
    }

    // 상품 숨김 메서드
    private Product saveHiddenProduct(Category category, String name, String price, int stock) {
        Product product = saveProduct(category, name, price, stock);
        product.hide();
        return productRepository.save(product);
    }

    // 검색 필터링 파라미터 record 로 저장 -> DTO 의 Builder
    // categoryIds 를 List 로 받는 근거는 상위 카테고리 클릭 시 그에 대한 하위 카테고리를 전부 조회해 목록에 보여줘야하기 때문
    private static ProductSearchCondition condition(List<Long> categoryIds,
                                                    String minPrice, String maxPrice, String keyword) {
        return new ProductSearchCondition(
                categoryIds,
                minPrice == null ? null : new BigDecimal(minPrice),
                maxPrice == null ? null : new BigDecimal(maxPrice),
                keyword);
    }

    private List<Product> searchAll(ProductSearchCondition condition) {
        return productRepository.findAll(ProductSpecifications.search(condition));
    }

    @Nested
    @DisplayName("조건을 주지 않으면 숨긴 상품만 빠진다")
    class NoCondition {

        @Test
        @DisplayName("보이는 상품 4건이 전부 나온다")
        void returnsAllVisible() {
            List<Product> found = searchAll(NO_CONDITION);

            assertThat(found).extracting(Product::getName)
                    .containsExactlyInAnyOrder("기본 반팔티", "오버핏 반팔티", "Oxford 셔츠", "슬랙스");
        }

        @Test
        @DisplayName("SOLD_OUT은 숨기지 않는다 — 품절과 삭제는 다른 축이다")
        void soldOutIsStillVisible() {
            List<Product> found = searchAll(NO_CONDITION);

            assertThat(found)
                    .filteredOn(p -> p.getStatus() == ProductStatus.SOLD_OUT)
                    .extracting(Product::getName)
                    .containsExactly("오버핏 반팔티");
        }
    }

    @Nested
    @DisplayName("단일 조건")
    class SingleCondition {

        @Test
        @DisplayName("카테고리 — 지정한 카테고리의 상품만")
        void byCategory() {
            List<Product> found = searchAll(condition(List.of(tshirt.getId()), null, null, null));

            assertThat(found).extracting(Product::getName)
                    .containsExactlyInAnyOrder("기본 반팔티", "오버핏 반팔티");
        }

        @Test
        @DisplayName("가격 하한 — minPrice 이상")
        void byMinPrice() {
            List<Product> found = searchAll(condition(null, "39900", null, null));

            assertThat(found).extracting(Product::getName)
                    .as("경계값 39900이 포함된다 — 이상이지 초과가 아니다")
                    .containsExactlyInAnyOrder("슬랙스", "Oxford 셔츠");
        }

        @Test
        @DisplayName("가격 상한 — maxPrice 이하")
        void byMaxPrice() {
            List<Product> found = searchAll(condition(null, null, "19900", null));

            assertThat(found).extracting(Product::getName)
                    .as("경계값 19900이 포함된다")
                    .containsExactly("기본 반팔티");
        }

        @Test
        @DisplayName("키워드 — 상품명 부분 일치")
        void byKeyword() {
            List<Product> found = searchAll(condition(null, null, null, "반팔"));

            assertThat(found).extracting(Product::getName)
                    .containsExactlyInAnyOrder("기본 반팔티", "오버핏 반팔티");
        }

        @Test
        @DisplayName("키워드 — 대소문자를 가리지 않는다")
        void keywordIsCaseInsensitive() {
            List<Product> found = searchAll(condition(null, null, null, "oxford"));

            assertThat(found).extracting(Product::getName)
                    .as("cb.lower()를 빼면 여기서 깨진다")
                    .containsExactly("Oxford 셔츠");
        }
    }

    @Nested
    @DisplayName("값이 없으면 조건이 빠진다")
    class AbsentConditionIsSkipped {

        @Test
        @DisplayName("빈 카테고리 목록은 조건 없음이다")
        void emptyCategoryListIsIgnored() {
            List<Product> found = searchAll(condition(List.of(), null, null, null));

            assertThat(found)
                    .as("빈 목록을 IN ()으로 번역하면 결과가 0건이 되어버린다")
                    .hasSize(4);
        }

        @Test
        @DisplayName("빈 키워드는 조건 없음이다")
        void blankKeywordIsIgnored() {
            assertThat(searchAll(condition(null, null, null, ""))).hasSize(4);
            assertThat(searchAll(condition(null, null, null, "   ")))
                    .as("공백만 있어도 LIKE '%%'가 되면 안 된다")
                    .hasSize(4);
        }
    }

    @Nested
    @DisplayName("복합 조건은 AND로 좁힌다")
    class CombinedCondition {

        @Test
        @DisplayName("카테고리 + 가격 범위")
        void categoryAndPriceRange() {
            List<Product> found = searchAll(
                    condition(List.of(tshirt.getId()), "20000", "35000", null));

            assertThat(found).extracting(Product::getName)
                    .containsExactly("오버핏 반팔티");
        }

        @Test
        @DisplayName("카테고리 + 가격 + 키워드 셋 전부")
        void allThree() {
            List<Product> found = searchAll(
                    condition(List.of(tshirt.getId()), "10000", "25000", "반팔"));

            assertThat(found).extracting(Product::getName)
                    .containsExactly("기본 반팔티");
        }

        @Test
        @DisplayName("교집합이 없으면 빈 결과다")
        void noMatch() {
            List<Product> found = searchAll(
                    condition(List.of(bottoms.getId()), null, null, "반팔"));

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("대분류를 펼친 id 목록이 들어오면 하위 상품까지 나온다")
    class CategoryExpansion {

        @Test
        @DisplayName("[대분류 + 소분류들]을 주면 양쪽 소분류 상품이 모두 나온다")
        void selfAndChildren() {
            List<Product> found = searchAll(
                    condition(List.of(tops.getId(), tshirt.getId(), shirt.getId()), null, null, null));

            assertThat(found).extracting(Product::getName)
                    .as("펼치는 책임은 서비스이고, 여기서는 IN으로 번역만 한다")
                    .containsExactlyInAnyOrder("기본 반팔티", "오버핏 반팔티", "Oxford 셔츠");
        }

        @Test
        @DisplayName("대분류 id만 주면 직접 붙은 상품만 — 상의에는 없다")
        void parentIdAloneMatchesNothingHere() {
            List<Product> found = searchAll(condition(List.of(tops.getId()), null, null, null));

            assertThat(found)
                    .as("상품은 소분류에 달려 있다. 펼치지 않고 넘기면 이렇게 빈다")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("숨긴 상품은 어떤 조합에서도 나오지 않는다")
    class HiddenIsNeverReturned {

        @Test
        @DisplayName("이름으로 정확히 지목해도 나오지 않는다")
        void notFoundEvenByExactKeyword() {
            List<Product> found = searchAll(condition(null, null, null, "단종"));

            assertThat(found)
                    .as("notHidden()이 search() 안에 붙박이라 호출자가 뺄 수 없다")
                    .isEmpty();
        }

        @Test
        @DisplayName("카테고리로 좁혀도 나오지 않는다")
        void notFoundByCategory() {
            List<Product> found = searchAll(condition(List.of(outer.getId()), null, null, null));

            assertThat(found)
                    .as("아우터에는 숨긴 코트 하나뿐이다")
                    .isEmpty();
        }

        @Test
        @DisplayName("가격 범위로 좁혀도 나오지 않는다")
        void notFoundByPrice() {
            List<Product> found = searchAll(condition(null, "90000", null, null));

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("페이징과 정렬")
    class PagingAndSorting {

        @Test
        @DisplayName("가격 오름차순으로 끊어 준다")
        void sortedByPriceAscending() {
            Page<Product> page = productRepository.findAll(
                    ProductSpecifications.search(NO_CONDITION),
                    PageRequest.of(0, 2, Sort.by("price").ascending()));

            assertThat(page.getContent()).extracting(Product::getName)
                    .containsExactly("기본 반팔티", "오버핏 반팔티");
            assertThat(page.getTotalElements())
                    .as("totalElements는 필터가 반영된 전체 건수다 — 숨긴 2건은 빠진다")
                    .isEqualTo(4);
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(page.hasNext()).isTrue();
            assertThat(page.hasPrevious()).isFalse();
        }

        @Test
        @DisplayName("필터가 걸리면 totalElements도 함께 줄어든다")
        void totalElementsReflectsFilter() {
            Page<Product> page = productRepository.findAll(
                    ProductSpecifications.search(condition(List.of(tshirt.getId()), null, null, null)),
                    PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("쿼리 메서드 둘")
    class QueryMethods {

        @Test
        @DisplayName("findByIdAndStatusNot — 보이는 상품은 찾아진다")
        void findVisibleById() {
            Optional<Product> found =
                    productRepository.findByIdAndStatusNot(basicTee.getId(), ProductStatus.HIDDEN);

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("기본 반팔티");
        }

        @Test
        @DisplayName("findByIdAndStatusNot — 숨긴 상품은 빈 Optional이다 (서비스가 P001로 바꾼다)")
        void hiddenIdReturnsEmpty() {
            Optional<Product> found =
                    productRepository.findByIdAndStatusNot(hiddenTee.getId(), ProductStatus.HIDDEN);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("findById — 숨긴 상품도 읽힌다 (ADMIN의 복구 통로)")
        void findByIdReadsHidden() {
            assertThat(productRepository.findById(hiddenTee.getId()))
                    .as("수정·삭제 경로가 이 메서드를 쓰는 이유다")
                    .isPresent();
        }

        @Test
        @DisplayName("existsByCategoryId — 상품이 붙어 있으면 true")
        void existsWhenProductAttached() {
            assertThat(productRepository.existsByCategoryId(tshirt.getId())).isTrue();
        }

        @Test
        @DisplayName("existsByCategoryId — 숨긴 상품만 있어도 true (행이 남아 FK가 걸린다)")
        void existsEvenWhenOnlyHidden() {
            assertThat(productRepository.existsByCategoryId(outer.getId()))
                    .as("soft delete라 행이 남는다 — 카테고리를 지우면 FK가 깨진다")
                    .isTrue();
        }

        @Test
        @DisplayName("existsByCategoryId — 상품이 없으면 false")
        void notExistsWhenEmpty() {
            assertThat(productRepository.existsByCategoryId(bag.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("발행되는 쿼리 수")
    class QueryCount {

        private Statistics statistics() {
            Statistics statistics = em.getEntityManager()
                    .getEntityManagerFactory()
                    .unwrap(SessionFactory.class)
                    .getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            return statistics;
        }

        @Test
        @DisplayName("@BatchSize — 상품 4건의 이미지를 읽어도 이미지 쿼리는 1회다")
        void imagesAreLoadedInOneQuery() {
            Statistics statistics = statistics();

            Page<Product> page = productRepository.findAll(
                    ProductSpecifications.search(NO_CONDITION), PageRequest.of(0, 20));
            page.getContent().forEach(product -> product.getImages().size());

            assertThat(page.getContent()).hasSize(4);
            assertThat(statistics.getPrepareStatementCount())
                    .as("content 1 + 이미지 배치 1 = 2. @BatchSize가 없으면 상품마다 이미지 쿼리가 "
                            + "붙어 5가 된다. count는 아래 테스트 참고 — 여기서는 발행되지 않는다")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("한 페이지에 다 들어가면 count 쿼리를 생략한다")
        void countIsSkippedOnSinglePage() {
            Statistics statistics = statistics();

            Page<Product> page = productRepository.findAll(
                    ProductSpecifications.search(NO_CONDITION), PageRequest.of(0, 20));

            assertThat(page.getTotalElements()).isEqualTo(4);
            assertThat(statistics.getPrepareStatementCount())
                    .as("Spring Data는 page 0이고 결과가 size보다 작으면 totalElements를 "
                            + "content 크기로 확정한다 — 셀 필요가 없다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("페이지가 나뉘면 count 쿼리가 함께 나간다")
        void countIsIssuedWhenPaged() {
            Statistics statistics = statistics();

            Page<Product> page = productRepository.findAll(
                    ProductSpecifications.search(NO_CONDITION), PageRequest.of(0, 2));
            page.getContent().forEach(product -> product.getImages().size());

            assertThat(page.getTotalElements()).isEqualTo(4);
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(statistics.getPrepareStatementCount())
                    .as("content 1 + count 1 + 이미지 배치 1 = 3. "
                            + "목록 조회가 실제로 몇 번 DB를 가는지가 여기서 확정된다")
                    .isEqualTo(3);
        }
    }
}
