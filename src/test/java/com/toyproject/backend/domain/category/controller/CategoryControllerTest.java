package com.toyproject.backend.domain.category.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyproject.backend.domain.auth.token.JwtProvider;
import com.toyproject.backend.domain.category.dto.CategoryCreateRequest;
import com.toyproject.backend.domain.category.dto.CategoryUpdateRequest;
import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [Phase 3 · 4단계] 카테고리 API 검증
 *
 * <p><b>무엇을 고정하는가</b><br>
 * 도메인 규칙은 3단계({@code CategoryServiceTest})가 이미 검증했다. 여기서 보는 것은 그 규칙이
 * <b>HTTP로 나올 때의 모양</b>이다 — 상태 코드, {@code Location}, 응답 바디, 그리고
 * <b>누가 호출할 수 있는가</b>.
 *
 * <p><b>서비스를 mock으로 대체하지 않는 이유</b><br>
 * 이 단계의 절반은 {@code SecurityConfig} 배선이고, 그것은 <b>실제 필터체인을 태워야만</b> 드러난다.
 * 8단계 {@code AuthControllerTest}는 HTTP 계약만 봐서 서비스를 걷어냈지만, 여기서는
 * permitAll·{@code hasRole("ADMIN")} 판정이 검증 대상이라 실제 체인과 DB가 필요하다
 * (9단계 {@code UserControllerTest}와 같은 선택이다).
 *
 * <p>토큰은 {@code JwtProvider}로 직접 만든다. ADMIN은 {@code promoteToAdmin()}으로 승격시키는데,
 * 권한이 <b>토큰 claim이 아니라 매 요청 DB에서 읽히기 때문에</b> 승격 후 재발급이 필요 없다
 * ({@code User.promoteToAdmin} Javadoc 참고).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("카테고리 API (Phase 3 · 4단계)")
class CategoryControllerTest {

    private static final String BASE_URI = "/api/categories";

    /** 존재하지 않는 id. 권한 판정이 대상 조회보다 <b>먼저</b>라는 것을 드러내는 데 쓴다. */
    private static final long ABSENT_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private String userBearer;
    private String adminBearer;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.ofKakao("kakao-3001", "user@kakao.com", "민준"));
        User admin = userRepository.save(User.ofKakao("kakao-3002", "admin@kakao.com", "관리자"));
        admin.promoteToAdmin();

        userBearer = "Bearer " + jwtProvider.createAccessToken(user);
        adminBearer = "Bearer " + jwtProvider.createAccessToken(admin);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Category saveRoot(String name, int displayOrder) {
        return categoryRepository.save(Category.root(name, displayOrder));
    }

    private Category saveChild(Category parent, String name, int displayOrder) {
        return categoryRepository.save(Category.child(parent, name, displayOrder));
    }

    private long dataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    @Nested
    @DisplayName("GET /api/categories — 비회원에게 열린 첫 API")
    class GetTree {

        @Test
        @DisplayName("토큰 없이도 트리를 받는다 — 소분류가 대분류 안에 중첩돼 나온다")
        void returnsTreeToAnonymous() throws Exception {
            Category top = saveRoot("상의", 0);
            saveChild(top, "티셔츠", 0);

            mockMvc.perform(get(BASE_URI))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].name").value("상의"))
                    .andExpect(jsonPath("$.data[0].children[0].name").value("티셔츠"))
                    .andExpect(jsonPath("$.data[1]").doesNotExist());
        }

        @Test
        @DisplayName("카테고리가 하나도 없으면 빈 배열이다 — 실패가 아니다")
        void returnsEmptyArray() throws Exception {
            mockMvc.perform(get(BASE_URI))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty())
                    .andExpect(jsonPath("$.error").doesNotExist());
        }
    }

    @Nested
    @DisplayName("POST /api/categories — ADMIN")
    class Create {

        @Test
        @DisplayName("201과 함께 Location을 돌려준다 — 절대 URL이 아니라 상대 경로다")
        void createsRootCategory() throws Exception {
            MvcResult result = mockMvc.perform(post(BASE_URI)
                            .header(HttpHeaders.AUTHORIZATION, adminBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CategoryCreateRequest("상의", 0, null))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("상의"))
                    .andExpect(jsonPath("$.data.displayOrder").value(0))
                    .andExpect(jsonPath("$.data.parentId").doesNotExist())
                    .andReturn();

            String location = result.getResponse().getHeader(HttpHeaders.LOCATION);

            assertThat(location).isEqualTo(BASE_URI + "/" + dataId(result));
            // 프록시 뒤에서 호스트·스킴이 어긋나지 않게 상대 경로로 내보낸다 (컨트롤러 Javadoc).
            assertThat(location).doesNotStartWith("http");
        }

        @Test
        @DisplayName("parentId를 주면 그 아래 소분류가 된다")
        void createsChildCategory() throws Exception {
            Category top = saveRoot("상의", 0);

            mockMvc.perform(post(BASE_URI)
                            .header(HttpHeaders.AUTHORIZATION, adminBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CategoryCreateRequest("티셔츠", 1, top.getId()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.parentId").value(top.getId()));
        }

        @Test
        @DisplayName("이름이 비면 C001(400)과 위반 필드를 돌려준다")
        void rejectsBlankName() throws Exception {
            mockMvc.perform(post(BASE_URI)
                            .header(HttpHeaders.AUTHORIZATION, adminBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CategoryCreateRequest(" ", 0, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("C001"))
                    .andExpect(jsonPath("$.error.errors[0].field").value("name"));
        }

        @Test
        @DisplayName("전시 순서가 음수면 C001(400)")
        void rejectsNegativeDisplayOrder() throws Exception {
            mockMvc.perform(post(BASE_URI)
                            .header(HttpHeaders.AUTHORIZATION, adminBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CategoryCreateRequest("상의", -1, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("C001"))
                    .andExpect(jsonPath("$.error.errors[0].field").value("displayOrder"));
        }

        @Test
        @DisplayName("부모가 없으면 CA001(404)")
        void rejectsAbsentParent() throws Exception {
            mockMvc.perform(post(BASE_URI)
                            .header(HttpHeaders.AUTHORIZATION, adminBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CategoryCreateRequest("티셔츠", 0, ABSENT_ID))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("CA001"));
        }

        @Test
        @DisplayName("부모가 이미 소분류면 CA003(400) — 2depth 제한이 HTTP 경계에도 드러난다")
        void rejectsThirdDepth() throws Exception {
            Category top = saveRoot("상의", 0);
            Category bottom = saveChild(top, "티셔츠", 0);

            mockMvc.perform(post(BASE_URI)
                            .header(HttpHeaders.AUTHORIZATION, adminBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CategoryCreateRequest("반팔", 0, bottom.getId()))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("CA003"));
        }
    }

    @Nested
    @DisplayName("PUT /api/categories/{id} — ADMIN")
    class Update {

        @Test
        @DisplayName("이름과 전시 순서가 바뀐다")
        void updatesCategory() throws Exception {
            Category top = saveRoot("상의", 0);

            mockMvc.perform(put(BASE_URI + "/" + top.getId())
                            .header(HttpHeaders.AUTHORIZATION, adminBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CategoryUpdateRequest("아우터", 3))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(top.getId()))
                    .andExpect(jsonPath("$.data.name").value("아우터"))
                    .andExpect(jsonPath("$.data.displayOrder").value(3));
        }

        @Test
        @DisplayName("대상이 없으면 CA001(404)")
        void rejectsAbsentTarget() throws Exception {
            mockMvc.perform(put(BASE_URI + "/" + ABSENT_ID)
                            .header(HttpHeaders.AUTHORIZATION, adminBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CategoryUpdateRequest("아우터", 0))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("CA001"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/categories/{id} — ADMIN")
    class Delete {

        @Test
        @DisplayName("200 + data:null로 응답한다 — 204를 쓰면 공통 응답 포맷이 깨진다")
        void deletesCategory() throws Exception {
            Category top = saveRoot("상의", 0);

            mockMvc.perform(delete(BASE_URI + "/" + top.getId())
                            .header(HttpHeaders.AUTHORIZATION, adminBearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.error").doesNotExist());

            assertThat(categoryRepository.findById(top.getId())).isEmpty();
        }

        @Test
        @DisplayName("하위가 있으면 CA002(409)")
        void rejectsDeleteWithChildren() throws Exception {
            Category top = saveRoot("상의", 0);
            saveChild(top, "티셔츠", 0);

            mockMvc.perform(delete(BASE_URI + "/" + top.getId())
                            .header(HttpHeaders.AUTHORIZATION, adminBearer))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CA002"));
        }
    }

    /**
     * 인가 판정. <b>이 프로젝트에서 처음으로 401과 403이 갈린다.</b>
     *
     * <p>대상 id를 존재하지 않는 값으로 쓴다 — 권한 판정이 필터체인에서 끝나므로 컨트롤러·서비스에
     * 닿지 않고, 따라서 <b>404가 아니라 403이 나와야 한다</b>. 여기가 뒤집히면 권한 없는 사용자가
     * 응답 코드만으로 카테고리의 존재 여부를 알아낼 수 있다.
     */
    @Nested
    @DisplayName("권한 — 쓰기는 ADMIN만")
    class Authorization {

        static Stream<Arguments> writeRequests() {
            return Stream.of(
                    Arguments.of("POST " + BASE_URI, post(BASE_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"상의\",\"displayOrder\":0}")),
                    Arguments.of("PUT " + BASE_URI + "/{id}", put(BASE_URI + "/" + ABSENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"상의\",\"displayOrder\":0}")),
                    Arguments.of("DELETE " + BASE_URI + "/{id}", delete(BASE_URI + "/" + ABSENT_ID)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("writeRequests")
        @DisplayName("토큰이 없으면 A001(401) — 익명은 EntryPoint가 받는다")
        void rejectsAnonymous(String name, MockHttpServletRequestBuilder request) throws Exception {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("A001"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("writeRequests")
        @DisplayName("USER면 A002(403) — 인증은 됐지만 권한이 없다")
        void rejectsNonAdmin(String name, MockHttpServletRequestBuilder request) throws Exception {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, userBearer))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("A002"));
        }

        @Test
        @DisplayName("USER도 트리 조회는 된다 — 막히는 것은 쓰기뿐이다")
        void allowsReadToNonAdmin() throws Exception {
            mockMvc.perform(get(BASE_URI).header(HttpHeaders.AUTHORIZATION, userBearer))
                    .andExpect(status().isOk());
        }
    }

    /**
     * 경로 변수 변환. {@code /api/categories/{id}}가 이 프로젝트 <b>최초의 경로 변수</b>다.
     *
     * <p><b>ADMIN 토큰을 실어야 하는 테스트다.</b> 권한 판정이 먼저라 토큰 없이 보내면 401에 걸려
     * 변환 단계까지 도달하지 못하고, 검증하려는 것이 가려진다.
     */
    @Nested
    @DisplayName("경로 변수 — 숫자가 아닌 id")
    class PathVariable {

        @ParameterizedTest(name = "{0}")
        // 마지막 값은 Long 범위를 넘는 숫자다 — 숫자처럼 보여도 변환은 똑같이 실패한다.
        @ValueSource(strings = {"abc", "1.5", "99999999999999999999"})
        @DisplayName("C001(400)으로 거절한다 — 핸들러가 없으면 C003(500)이 된다")
        void rejectsNonNumericId(String id) throws Exception {
            mockMvc.perform(delete(BASE_URI + "/" + id)
                            .header(HttpHeaders.AUTHORIZATION, adminBearer))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("C001"))
                    .andExpect(jsonPath("$.error.errors[0].field").value("id"));
        }
    }
}
