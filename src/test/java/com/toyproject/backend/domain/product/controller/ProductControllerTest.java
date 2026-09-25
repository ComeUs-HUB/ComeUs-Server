package com.toyproject.backend.domain.product.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyproject.backend.domain.auth.token.JwtProvider;
import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
import com.toyproject.backend.domain.product.dto.ProductCreateRequest;
import com.toyproject.backend.domain.product.dto.ProductUpdateRequest;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.repository.ProductRepository;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [Phase 3 · 8단계] 상품 조회 API의 HTTP 계약 검증.
 *
 * <p>{@code @SpringBootTest}로 <b>실제 필터체인</b>을 태운다 — permitAll 배선은 모킹으로
 * 검증되지 않는다({@code CategoryControllerTest}와 같은 이유).
 *
 * <p>카테고리 트리만 {@code @BeforeEach}가 만들고 상품은 각 테스트가 심는다 —
 * "상품이 하나도 없는" 경우도 케이스이기 때문이다.
 *
 * <p>쓰기 블록은 인가 배선과 HTTP 계약만 본다 — 도메인 규칙은 {@code ProductServiceTest}가 맡는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("상품 API (Phase 3 · 8단계)")
class ProductControllerTest {

    private static final String BASE_URI = "/api/products";

    private static final long ABSENT_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @PersistenceContext
    private EntityManager em;

    private Category tops;
    private Category tshirt;
    private Category shirt;
    private Category bottoms;

    @BeforeEach
    void setUp() {
        tops = categoryRepository.save(Category.root("상의", 1));
        bottoms = categoryRepository.save(Category.root("하의", 2));
        tshirt = categoryRepository.save(Category.child(tops, "티셔츠", 1));
        shirt = categoryRepository.save(Category.child(tops, "셔츠", 2));
    }

    /**
     * 보이는 상품 4건 + 숨긴 상품 1건.
     *
     * <pre>
     * 상의(대) ├ 티셔츠(소) ├ 기본 반팔티   19,900
     *          │             ├ 오버핏 반팔티 29,900
     *          │             └ (단종) 티셔츠 9,900 HIDDEN
     *          └ 셔츠(소)   └ Oxford 셔츠  49,900
     * 하의(대)                슬랙스        39,900
     * </pre>
     */
    private void seedCatalog() {
        saveProduct(tshirt, "기본 반팔티", "19900", 10);
        saveProduct(tshirt, "오버핏 반팔티", "29900", 5);
        saveProduct(shirt, "Oxford 셔츠", "49900", 5);
        saveProduct(bottoms, "슬랙스", "39900", 3);
        saveHidden(tshirt, "단종 티셔츠", "9900", 1);

        // 영속성 컨텍스트가 응답을 대신 채우지 않도록, 조회는 DB에서 다시 읽게 한다.
        em.flush();
        em.clear();
    }

    private Product saveProduct(Category category, String name, String price, int stock) {
        Product product = Product.create(category, name, name + " 설명", new BigDecimal(price), stock);
        product.addImage("https://cdn.example.com/" + name + "-1.jpg");
        return productRepository.save(product);
    }

    private Product saveHidden(Category category, String name, String price, int stock) {
        Product product = saveProduct(category, name, price, stock);
        product.hide();
        return productRepository.save(product);
    }

    /** {@code content[*].name}. 기본 정렬이 {@code createdAt} 하나뿐이라 순서는 고정하지 않는다. */
    private List<String> namesOf(MvcResult result) throws Exception {
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("content");

        List<String> names = new ArrayList<>();
        content.forEach(node -> names.add(node.path("name").asText()));
        return names;
    }

    @Nested
    @DisplayName("GET /api/products — 비회원에게 열려 있다")
    class GetList {

        @Test
        @DisplayName("토큰 없이 200이고 PageResponse 일곱 필드를 모두 담는다")
        void returnsPageResponseToAnonymous() throws Exception {
            seedCatalog();

            mockMvc.perform(get(BASE_URI))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.error").doesNotExist())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(4))
                    .andExpect(jsonPath("$.data.totalPages").value(1))
                    .andExpect(jsonPath("$.data.currentPage").value(0))
                    .andExpect(jsonPath("$.data.pageSize").value(20))
                    .andExpect(jsonPath("$.data.hasNext").value(false))
                    .andExpect(jsonPath("$.data.hasPrevious").value(false));
        }

        @Test
        @DisplayName("상품이 하나도 없으면 빈 content에 totalElements 0 — 실패가 아니다")
        void emptyCatalogIsNotFailure() throws Exception {
            mockMvc.perform(get(BASE_URI))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0))
                    .andExpect(jsonPath("$.data.totalPages").value(0))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("숨긴 상품은 목록에 없다 — 권한과 무관하게 빠진다")
        void hiddenProductIsExcluded() throws Exception {
            /**
             1. mockMvc.perform(get(...))을 실행하고 status().isOk()(-> HTTP 200) & totalElements == 4를 확인한 뒤 .
             andReturn()으로 result를 받는다
             2. namesOf(result)로 응답 바디의 content 배열에서 name 필드만 뽑아 List<String>으로 만든다
             3. 그 리스트에 doesNotContain("단종 티셔츠") — 숨긴 상품 이름이 없는지 확인한다
             4. 이어서 containsExactlyInAnyOrder(...)로 나머지 4개 상품 이름이 정확히 다 있는지 확인한다
             * */
            seedCatalog();

            /**
             * get(BASE_URI) 가 가짜 HTTP 요청을 던져 실제 Spring MVC 스택 전체를 통과시킨다.
             * 실제 ProductController 의 getList 엔드포인트로 요청이 간다.
             * */
            MvcResult result = mockMvc.perform(get(BASE_URI))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(4))
                    .andReturn();

            assertThat(namesOf(result))
                    .as("HIDDEN은 Specification에 붙박이라 호출자가 뺄 수 없다 (ADR 0013)")
                    .doesNotContain("단종 티셔츠")
                    .containsExactlyInAnyOrder("기본 반팔티", "오버핏 반팔티", "Oxford 셔츠", "슬랙스");
        }

        @Test
        @DisplayName("대분류 categoryId를 주면 하위 소분류 상품까지 나온다")
        void topCategoryIncludesChildren() throws Exception {
            seedCatalog();

            MvcResult result = mockMvc.perform(get(BASE_URI).param("categoryId", String.valueOf(tops.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(3))
                    .andReturn();

            // 서비스 테스트는 CategoryService를 mock으로 두므로 펼치기가 여기서 처음 끝까지 확인된다.
            assertThat(namesOf(result))
                    .containsExactlyInAnyOrder("기본 반팔티", "오버핏 반팔티", "Oxford 셔츠")
                    .doesNotContain("슬랙스");
        }

        @Test
        @DisplayName("소분류 categoryId는 그 소분류 상품만 돌려준다")
        void childCategoryReturnsOwnProducts() throws Exception {
            seedCatalog();

            MvcResult result = mockMvc.perform(get(BASE_URI).param("categoryId", String.valueOf(shirt.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andReturn();

            assertThat(namesOf(result)).containsExactly("Oxford 셔츠");
        }

        @Test
        @DisplayName("없는 categoryId면 CA001(404) — 빈 목록이 아니다")
        void absentCategoryIsCa001() throws Exception {
            seedCatalog();

            mockMvc.perform(get(BASE_URI).param("categoryId", String.valueOf(ABSENT_ID)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("CA001"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("minPrice·maxPrice·keyword를 조합하면 AND로 걸린다")
        void filtersCombineWithAnd() throws Exception {
            seedCatalog();

            MvcResult result = mockMvc.perform(get(BASE_URI)
                            .param("keyword", "반팔")
                            .param("minPrice", "20000")
                            .param("maxPrice", "35000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andReturn();

            // keyword만이면 둘, 가격만이면 오버핏·슬랙스 둘이다 — 교집합이 하나 남는다.
            assertThat(namesOf(result)).containsExactly("오버핏 반팔티");
        }

        @Test
        @DisplayName("필터에 걸리는 상품이 없으면 빈 content에 200")
        void noMatchIsEmptyPage() throws Exception {
            seedCatalog();

            mockMvc.perform(get(BASE_URI).param("keyword", "존재하지 않는 상품명"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/products — 페이징과 정렬")
    class Paging {

        /**
         * {@code createdAt}로만 정렬하면 순서가 등록 시각에 의존한다. 같은 트랜잭션 안에서
         * 연달아 저장하면 {@code DATETIME(6)}이라도 값이 갈릴지 보장되지 않으므로,
         * 정렬 순서를 확인하는 테스트에서만 간격을 두고 둘을 심는다.
         */
        private void seedTwoWithGap() throws InterruptedException {
            saveProduct(tshirt, "먼저 등록한 상품", "10000", 1);
            Thread.sleep(10);
            saveProduct(tshirt, "나중 등록한 상품", "20000", 1);
            em.flush();
            em.clear();
        }

        @Test
        @DisplayName("파라미터가 없으면 size 20, createdAt 내림차순이 기본이다")
        void defaultsToCreatedAtDescWithSizeTwenty() throws Exception {
            seedTwoWithGap();

            MvcResult result = mockMvc.perform(get(BASE_URI))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pageSize").value(20))
                    .andReturn();

            assertThat(namesOf(result))
                    .as("나중에 등록한 상품이 먼저 나와야 createdAt 내림차순이다")
                    .containsExactly("나중 등록한 상품", "먼저 등록한 상품");
        }

        @Test
        @DisplayName("sort=price,asc 로 정렬이 바뀐다")
        void sortsByPriceAscending() throws Exception {
            seedCatalog();

            MvcResult result = mockMvc.perform(get(BASE_URI).param("sort", "price,asc"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(namesOf(result))
                    .containsExactly("기본 반팔티", "오버핏 반팔티", "슬랙스", "Oxford 셔츠");
        }
        
        /**
         * ProductController.sanitize()(ProductController.java:81)가 실제로 방어막 역할을 한다는 걸 증명한다.
         * */
        @Test
        @DisplayName("sort=foo는 C001(400)이다 — 검증이 없으면 PropertyReferenceException이 C003(500)으로 샌다")
        void rejectsUnknownSortField() throws Exception {
            mockMvc.perform(get(BASE_URI).param("sort", "foo"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("C001"));
        }

        @Test
        @DisplayName("sort 필드가 여러 개일 때 하나라도 허용 목록 밖이면 C001")
        void rejectsIfAnySortFieldIsUnknown() throws Exception {
            mockMvc.perform(get(BASE_URI)
                            .param("sort", "price,asc")
                            .param("sort", "foo,desc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("C001"));
        }

        @Test
        @DisplayName("size=9999는 100으로 잘린다 — 거절이 아니라 절단이다")
        void clampsOversizedPageSize() throws Exception {
            seedCatalog();

            mockMvc.perform(get(BASE_URI).param("size", "9999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pageSize").value(100))
                    .andExpect(jsonPath("$.data.totalElements").value(4));
        }

        @Test
        @DisplayName("page가 범위를 넘으면 빈 content에 200 — 404가 아니다")
        void outOfRangePageIsEmptyNotNotFound() throws Exception {
            seedCatalog();

            mockMvc.perform(get(BASE_URI).param("page", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(4))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }
    }

    @Nested
    @DisplayName("GET /api/products/{id}")
    class GetDetail {

        private long saveAndReadId(Product product) {
            productRepository.save(product);
            em.flush();
            em.clear();
            return product.getId();
        }

        @Test
        @DisplayName("토큰 없이 200이고 상세 필드를 담는다")
        void returnsDetailToAnonymous() throws Exception {
            long id = saveAndReadId(saveProduct(tshirt, "기본 반팔티", "19900", 10));

            mockMvc.perform(get(BASE_URI + "/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(id))
                    .andExpect(jsonPath("$.data.name").value("기본 반팔티"))
                    .andExpect(jsonPath("$.data.description").value("기본 반팔티 설명"))
                    .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                    .andExpect(jsonPath("$.data.categoryId").value(tshirt.getId()))
                    .andExpect(jsonPath("$.data.imageUrls").isArray())
                    .andExpect(jsonPath("$.data.imageUrls[0]").value("https://cdn.example.com/기본 반팔티-1.jpg"));
        }

        @Test
        @DisplayName("재고 0인 상품은 status가 SOLD_OUT으로 나간다")
        void soldOutProductShowsSoldOutStatus() throws Exception {
            long id = saveAndReadId(saveProduct(tshirt, "오버핏 반팔티", "29900", 0));

            mockMvc.perform(get(BASE_URI + "/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SOLD_OUT"));
        }

        @Test
        @DisplayName("응답 바디에 stockQuantity 키 자체가 없다 — 공개 경로라 재고가 새면 안 된다 (ADR 0015)")
        void responseHasNoStockQuantityField() throws Exception {
            long id = saveAndReadId(saveProduct(tshirt, "기본 반팔티", "19900", 10));

            mockMvc.perform(get(BASE_URI + "/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.stockQuantity").doesNotExist());
        }

        @Test
        @DisplayName("이미지가 없으면 imageUrls는 빈 배열이다")
        void emptyImagesWhenNoImage() throws Exception {
            // saveProduct는 이미지를 하나 붙이므로 여기서는 Product.create만 직접 쓴다.
            Product product = Product.create(tshirt, "이미지 없는 상품", "설명", new BigDecimal("10000"), 1);
            long id = saveAndReadId(product);

            mockMvc.perform(get(BASE_URI + "/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.imageUrls").isArray())
                    .andExpect(jsonPath("$.data.imageUrls").isEmpty());
        }

        @Test
        @DisplayName("없는 id면 P001(404)")
        void absentIdIsP001() throws Exception {
            mockMvc.perform(get(BASE_URI + "/{id}", ABSENT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("P001"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("숨긴 상품도 P001(404) — 목록에서 빠지는 것과 같은 기준이다")
        void hiddenProductIsP001() throws Exception {
            long id = saveAndReadId(saveHidden(tshirt, "단종 티셔츠", "9900", 1));

            mockMvc.perform(get(BASE_URI + "/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("P001"));
        }

        @Test
        @DisplayName("ADMIN 토큰으로 호출해도 숨긴 상품은 여전히 404다")
        void hiddenProductIsP001EvenForAdmin() throws Exception {
            long id = saveAndReadId(saveHidden(tshirt, "단종 티셔츠", "9900", 1));

            User admin = userRepository.save(User.ofKakao("kakao-product-admin", "admin@kakao.com", "관리자"));
            admin.promoteToAdmin();
            String adminBearer = "Bearer " + jwtProvider.createAccessToken(admin);

            // 권한으로 분기하지 않는다는 ADR 0013이 HTTP 경계에서도 성립하는지 확인한다.
            mockMvc.perform(get(BASE_URI + "/{id}", id).header(HttpHeaders.AUTHORIZATION, adminBearer))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("P001"));
        }

        @Test
        @DisplayName("숫자가 아닌 경로 변수는 C001(400)이다 (500이 아님)")
        void nonNumericPathVariableIsC001() throws Exception {
            mockMvc.perform(get(BASE_URI + "/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("C001"));
        }
    }

    /**
     * 쓰기 셋 — <b>ADMIN 인가 배선과 HTTP 계약만</b> 본다.
     *
     * <p>등록·수정·삭제의 도메인 규칙은 {@code ProductServiceTest}가 이미 고정했다. 여기서는
     * mock으로는 드러나지 않는 것만 남겼다 — {@code hasRole} 판정, {@code Location} 헤더,
     * {@code @Valid} 결선, 그리고 DELETE가 204가 아니라는 것.
     */
    @Nested
    @DisplayName("쓰기 — ADMIN만")
    class Write {

        // User 생성 후 admin 으로 승격 (user.promoteToAdmin())
        private String bearerOf(String providerId, boolean admin) {
            User user = userRepository.save(User.ofKakao(providerId, providerId + "@kakao.com", "테스터"));
            if (admin) {
                user.promoteToAdmin();
            }
            return "Bearer " + jwtProvider.createAccessToken(user);
        }

        private String createBody() throws Exception {
            return objectMapper.writeValueAsString(new ProductCreateRequest(
                    tshirt.getId(), "기본 반팔티", "설명", new BigDecimal("19900"), 10,
                    List.of("https://cdn.example.com/a.jpg")));
        }

        @Test
        @DisplayName("비회원은 401(A001)이다 — 익명은 권한 부족이어도 EntryPoint로 간다")
        void anonymousIsUnauthorized() throws Exception {
            mockMvc.perform(post(BASE_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("A001"));
        }

        @Test
        @DisplayName("USER 토큰은 403(A002)이다 — hasRole 판정은 실제 필터체인에서만 드러난다")
        void userIsForbidden() throws Exception {
            mockMvc.perform(post(BASE_URI)
                            .header(HttpHeaders.AUTHORIZATION, bearerOf("kakao-user", false))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("A002"));
        }

        @Test
        @DisplayName("PUT·DELETE도 USER에게 막힌다 — 단건 경로 매처가 따로 걸려 있다")
        void userIsForbiddenOnItemPath() throws Exception {
            String userBearer = bearerOf("kakao-user", false);
            long id = saveProduct(tshirt, "기본 반팔티", "19900", 10).getId();

            // POST는 컬렉션 경로, PUT·DELETE는 PRODUCTS_ITEM_PATH라 매처가 갈린다.
            mockMvc.perform(put(BASE_URI + "/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, userBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody()))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete(BASE_URI + "/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, userBearer))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN 등록은 201 + Location이며 절대 URL이 아니라 상대 경로다")
        void adminCreatesWithRelativeLocation() throws Exception {
            MvcResult result = mockMvc.perform(post(BASE_URI)
                            .header(HttpHeaders.AUTHORIZATION, bearerOf("kakao-admin", true))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("기본 반팔티"))
                    .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                    .andReturn();

            String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
            long id = objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("data").path("id").asLong();

            assertThat(location).isEqualTo(BASE_URI + "/" + id);
            assertThat(location).doesNotStartWith("http");
        }

        @Test
        @DisplayName("요청 바디가 규칙을 어기면 C001(400)이다 — @Valid가 걸려 있는지 본다")
        void invalidBodyIsC001() throws Exception {
            String blankName = objectMapper.writeValueAsString(new ProductCreateRequest(
                    tshirt.getId(), " ", "설명", new BigDecimal("19900"), 10, List.of()));

            mockMvc.perform(post(BASE_URI)
                            .header(HttpHeaders.AUTHORIZATION, bearerOf("kakao-admin", true))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(blankName))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("C001"));
        }

        @Test
        @DisplayName("ADMIN 삭제는 200 + data:null이다 — 204면 공통 포맷이 깨진다")
        void adminDeleteReturnsOkWithNullData() throws Exception {
            long id = saveProduct(tshirt, "기본 반팔티", "19900", 10).getId();

            mockMvc.perform(delete(BASE_URI + "/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, bearerOf("kakao-admin", true)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }
}
