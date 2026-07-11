# Clothing Store Backend API — 프로젝트 진행 순서 (A~Z)

> **기술 스택**: Spring Boot 3.5.0 · Java 21 · JPA · MySQL · Gradle (Kotlin DSL)  
> **목표**: 의류 판매 웹사이트 REST API 서버 구축 및 배포  
> **작성 기준**: PRD v1.0 기반

---

## 전체 Phase 요약

| Phase | 단계 | 내용 |
|-------|------|------|
| 환경 설정 | A ~ C | 프로젝트 초기화, 의존성, 설정 파일 |
| 글로벌 기반 | D ~ F | 공통 응답, 예외 처리, 페이지네이션 |
| 인증/인가 | G ~ K | User 도메인, Security, JWT |
| 카테고리/상품 | L ~ M | Category, Product 도메인 |
| 구매 플로우 | N ~ P | Cart, Order, Payment 도메인 |
| 리뷰 | Q | Review 도메인 |
| 마무리 | R ~ S | RBAC 완성, 테스트 코드 |
| 배포 | T ~ Z | Docker, CI/CD, 서버 배포, 모니터링 |

---

## Phase 0 — 환경 설정

### A. 프로젝트 초기화

**목적**: Spring Boot 3.5.0 기반 프로젝트 골격을 생성하고 기본 패키지 구조를 잡는다.

**작업 내용**

- [Spring Initializr](https://start.spring.io) 에서 아래 옵션으로 프로젝트 생성
  - Project: Gradle - Kotlin DSL
  - Language: Java 21
  - Spring Boot: 3.5.0
  - Packaging: Jar
- 패키지 구조 직접 생성

```
com.toyproject.backend
├── domain
│   ├── user
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
│   ├── product
│   ├── category
│   ├── cart
│   ├── order
│   ├── payment
│   └── review
└── global
    ├── config        # Security, JPA, Web 설정
    ├── exception     # GlobalExceptionHandler, BusinessException
    ├── response      # ApiResponse 공통 래퍼
    └── security      # JWT 필터, UserDetailsService
```

- `.gitignore` 설정 (application-prod.yml, .env 파일 제외)
- Git 저장소 초기화 및 첫 커밋

**체크포인트**
- [ ] `./gradlew bootRun` 으로 서버 정상 기동 확인
- [ ] 패키지 구조 생성 완료

---

### B. build.gradle.kts 의존성 설정

**목적**: 프로젝트에 필요한 모든 라이브러리를 한 번에 정의한다.

**작업 내용**

```kotlin
dependencies {
    // Spring Boot Core
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // MySQL
    runtimeOnly("com.mysql:mysql-connector-j")

    // JWT (jjwt 0.12.x)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
```

**참고 사항**
- jjwt는 반드시 `0.12.x` 버전 사용 (`io.jsonwebtoken:jjwt-api` 패키지명 주의)
- `spring-boot-starter-validation`은 `@Valid`, `@NotBlank`, `@Size` 등에 필수

**체크포인트**
- [ ] `./gradlew dependencies` 실행 시 의존성 충돌 없음
- [ ] Lombok `@Data` 어노테이션 정상 동작 확인

---

### C. application.yml 설정

**목적**: 개발/운영 환경을 분리하고 DB, JPA, JWT, 서버 포트를 설정한다.

**작업 내용**

`src/main/resources/application.yml` (공통 베이스)
```yaml
spring:
  profiles:
    active: dev

server:
  port: 8080

jwt:
  secret: ${JWT_SECRET}          # 환경변수로 주입
  access-expiration: 1800000     # 30분 (ms)
  refresh-expiration: 604800000  # 7일 (ms)
```

`src/main/resources/application-dev.yml` (개발용)
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/clothing_store?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQLDialect
```

`src/main/resources/application-prod.yml` (운영용 — `.gitignore` 에 추가)
```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate   # 운영에서는 validate 또는 none 권장
    show-sql: false
```

**체크포인트**
- [ ] 개발 환경에서 MySQL 연결 성공
- [ ] `application-prod.yml`이 Git에 올라가지 않음 확인

---

## Phase 1 — 글로벌 기반

### D. 공통 응답 포맷 구현 (ApiResponse)

**목적**: 모든 API 응답을 일관된 구조로 반환하는 공통 래퍼 클래스를 만든다.

**작업 내용**

PRD 4.2 기준 응답 포맷:
```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

`global/response/ApiResponse.java`
```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
```

**체크포인트**
- [ ] 제네릭 타입 T가 JSON 직렬화 정상 동작
- [ ] `null` 데이터도 응답에 포함되는지 확인 (필요 시 `@JsonInclude` 설정)

---

### E. 전역 예외 처리 구현 (GlobalExceptionHandler)

**목적**: 도메인별 비즈니스 예외를 계층적으로 정의하고, 전역에서 일관되게 처리한다.

**작업 내용**

`global/exception/ErrorCode.java` — 에러 코드 Enum 정의
```java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(400, "C001", "유효하지 않은 입력값입니다."),
    RESOURCE_NOT_FOUND(404, "C002", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "C003", "서버 내부 오류가 발생했습니다."),

    // Auth
    DUPLICATE_EMAIL(409, "U001", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(401, "U002", "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(401, "U003", "인증이 필요합니다."),
    FORBIDDEN(403, "U004", "접근 권한이 없습니다."),
    INVALID_TOKEN(401, "U005", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(401, "U006", "만료된 토큰입니다."),

    // Product
    PRODUCT_NOT_FOUND(404, "P001", "상품을 찾을 수 없습니다."),
    OUT_OF_STOCK(409, "P002", "재고가 부족합니다."),

    // Category
    CATEGORY_NOT_FOUND(404, "CA001", "카테고리를 찾을 수 없습니다."),
    CATEGORY_HAS_CHILDREN(409, "CA002", "하위 카테고리가 존재하여 삭제할 수 없습니다."),

    // Cart
    CART_ITEM_NOT_FOUND(404, "CT001", "장바구니 항목을 찾을 수 없습니다."),
    CART_QUANTITY_EXCEEDED(409, "CT002", "재고를 초과한 수량입니다."),

    // Order
    ORDER_NOT_FOUND(404, "O001", "주문을 찾을 수 없습니다."),
    ORDER_CANCEL_NOT_ALLOWED(409, "O002", "배송 중인 주문은 취소할 수 없습니다."),

    // Payment
    PAYMENT_NOT_FOUND(404, "PAY001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_ALREADY_EXISTS(409, "PAY002", "이미 결제가 완료된 주문입니다."),

    // Review
    REVIEW_NOT_FOUND(404, "R001", "리뷰를 찾을 수 없습니다."),
    REVIEW_ALREADY_EXISTS(409, "R002", "이미 리뷰를 작성한 상품입니다."),
    REVIEW_NOT_ALLOWED(403, "R003", "배송 완료된 주문에 한해 리뷰를 작성할 수 있습니다.");

    private final int status;
    private final String code;
    private final String message;
}
```

`global/exception/BusinessException.java`
```java
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

도메인별 예외 클래스 예시:
```java
// 각 도메인 패키지 내 exception 폴더에 위치
public class ProductNotFoundException extends BusinessException {
    public ProductNotFoundException() {
        super(ErrorCode.PRODUCT_NOT_FOUND);
    }
}
```

`global/exception/GlobalExceptionHandler.java`
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: {}", e.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getStatus(), errorCode.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(400)
                .body(ApiResponse.error(400, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error: ", e);
        return ResponseEntity
                .status(500)
                .body(ApiResponse.error(500, ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
```

**체크포인트**
- [ ] 존재하지 않는 리소스 요청 시 404 + 커스텀 메시지 반환
- [ ] `@Valid` 실패 시 400 응답 및 필드 오류 메시지 반환

---

### F. 페이지네이션 공통 구조 구현

**목적**: 목록 조회 API에서 일관된 페이지네이션 응답을 제공한다.

**작업 내용**

PRD 4.4 기준 — `totalElements`, `totalPages`, `currentPage` 포함

`global/response/PageResponse.java`
```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PageResponse<T> {

    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;

    public static <T> PageResponse<T> of(Page<T> page) {
        PageResponse<T> response = new PageResponse<>();
        response.content = page.getContent();
        response.totalElements = page.getTotalElements();
        response.totalPages = page.getTotalPages();
        response.currentPage = page.getNumber();
        response.pageSize = page.getSize();
        response.hasNext = page.hasNext();
        response.hasPrevious = page.hasPrevious();
        return response;
    }
}
```

컨트롤러 사용 예시:
```java
// GET /api/products?page=0&size=20&sort=createdAt,desc
@GetMapping
public ApiResponse<PageResponse<ProductResponse>> getProducts(Pageable pageable) {
    Page<ProductResponse> page = productService.getProducts(pageable);
    return ApiResponse.success(PageResponse.of(page));
}
```

**체크포인트**
- [ ] `Pageable` 파라미터 바인딩 정상 동작 (`page`, `size`, `sort`)
- [ ] 응답에 `totalElements`, `totalPages`, `currentPage` 포함 확인

---

## Phase 2 — 인증/인가 (User 도메인)

### G. User 엔티티 / Repository 구현

**목적**: 회원 도메인의 핵심 엔티티를 설계하고 JPA 기반 데이터 접근 계층을 구성한다.

**작업 내용**

`domain/user/entity/User.java`
```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public User(String email, String password, String name, String phone, String address, Role role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.role = role;
    }

    public void updateProfile(String name, String phone, String address) {
        this.name = name;
        this.phone = phone;
        this.address = address;
    }
}
```

`domain/user/entity/Role.java`
```java
public enum Role {
    USER, ADMIN
}
```

`domain/user/repository/UserRepository.java`
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

`global/config/JpaConfig.java` — Auditing 활성화
```java
@Configuration
@EnableJpaAuditing
public class JpaConfig {}
```

**체크포인트**
- [ ] DDL 자동 생성으로 `users` 테이블 정상 생성
- [ ] `existsByEmail()` 쿼리 정상 동작

---

### H. Spring Security 기반 설정

**목적**: Spring Security 6.x 방식으로 SecurityFilterChain을 구성하고 인증/인가 기반을 완성한다.

**참고**: Spring Boot 3.x부터 `WebSecurityConfigurerAdapter` 제거됨 → `SecurityFilterChain` Bean 방식 사용

**작업 내용**

`global/security/CustomUserDetailsService.java`
```java
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }
}
```

`global/config/SecurityConfig.java`
```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)   // 401
                        .accessDeniedHandler(jwtAccessDeniedHandler))             // 403
                .authorizeHttpRequests(auth -> auth
                        // 비회원 접근 허용
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/*/reviews").permitAll()
                        // ADMIN 전용
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                        // 그 외 모두 인증 필요
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

`global/security/JwtAuthenticationEntryPoint.java` — 401 처리
```java
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"code\":401,\"message\":\"인증이 필요합니다.\",\"data\":null}");
    }
}
```

`global/security/JwtAccessDeniedHandler.java` — 403 처리
```java
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("{\"code\":403,\"message\":\"접근 권한이 없습니다.\",\"data\":null}");
    }
}
```

**체크포인트**
- [ ] 인증 없이 `/api/products` 접근 → 200 정상
- [ ] 인증 없이 `/api/cart` 접근 → 401 반환
- [ ] USER 토큰으로 `/api/admin/**` 접근 → 403 반환

---

### I. JWT 유틸리티 구현 (JwtProvider)

**목적**: jjwt 0.12.x API 기준으로 Access Token, Refresh Token 생성·검증·파싱 기능을 구현한다.

**작업 내용**

`global/security/JwtProvider.java`
```java
@Component
@Slf4j
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessExpiration;
    private final long refreshExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration}") long accessExpiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    // Access Token 생성
    public String generateAccessToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiration))
                .signWith(secretKey)
                .compact();
    }

    // Refresh Token 생성
    public String generateRefreshToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(secretKey)
                .compact();
    }

    // 토큰에서 이메일 추출
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    // 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰: {}", e.getMessage());
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 JWT 토큰: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

**Refresh Token 저장 전략**
- 현재: DB의 `refresh_tokens` 테이블에 저장 (email, token, expiredAt)
- 추후: Redis 도입 시 `StringRedisTemplate`으로 교체 가능 (PRD 7번 미결 사항)

`domain/user/entity/RefreshToken.java`
```java
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Builder
    public RefreshToken(String email, String token, LocalDateTime expiredAt) {
        this.email = email;
        this.token = token;
        this.expiredAt = expiredAt;
    }

    public void rotate(String newToken, LocalDateTime newExpiredAt) {
        this.token = newToken;
        this.expiredAt = newExpiredAt;
    }
}
```

**체크포인트**
- [ ] Access Token 만료 시간 30분 확인 (jwt.io 디코더 활용)
- [ ] Refresh Token DB 저장 및 조회 정상 동작

---

### J. JWT 인증 필터 구현 (JwtAuthenticationFilter)

**목적**: 모든 요청에서 JWT를 추출하여 검증하고 SecurityContext에 인증 정보를 저장한다.

**작업 내용**

`global/security/JwtAuthenticationFilter.java`
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtProvider.validateToken(token)) {
            String email = jwtProvider.getEmailFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

**체크포인트**
- [ ] `Authorization: Bearer {token}` 헤더로 인증 정상 통과
- [ ] 토큰 없이 인증 필요 API 요청 시 401 반환

---

### K. 인증 API 구현 (AuthController / UserController)

**목적**: 회원가입, 로그인, 토큰 재발급, 로그아웃, 내 정보 CRUD API를 완성한다.

**작업 내용**

구현 API 목록:

| Method | URI | 설명 |
|--------|-----|------|
| POST | /api/auth/signup | 회원가입 |
| POST | /api/auth/login | 로그인 (Access + Refresh Token 발급) |
| POST | /api/auth/refresh | Refresh Token으로 Access Token 재발급 |
| POST | /api/auth/logout | 로그아웃 (Refresh Token 삭제) |
| GET | /api/users/me | 내 정보 조회 |
| PUT | /api/users/me | 내 정보 수정 |
| DELETE | /api/users/me | 회원 탈퇴 |

주요 비즈니스 로직:
- 회원가입 시 이메일 중복 체크 → `DUPLICATE_EMAIL` 예외
- 비밀번호 `BCryptPasswordEncoder`로 암호화 후 저장
- 로그인 성공 시 Access Token(30분) + Refresh Token(7일) 발급
- Refresh Token은 DB에 upsert 방식으로 저장 (Refresh Token Rotation)
- 로그아웃 시 DB에서 Refresh Token 삭제
- 현재 로그인 사용자는 `@AuthenticationPrincipal UserDetails` 로 주입

**체크포인트**
- [ ] 회원가입 → 로그인 → `/api/users/me` 전체 플로우 Postman 테스트 통과
- [ ] 만료된 Access Token + 유효한 Refresh Token으로 토큰 재발급 성공
- [ ] 로그아웃 후 Refresh Token 사용 시 에러 반환

---

## Phase 3 — 카테고리 / 상품

### L. Category 도메인 구현

**목적**: 대분류/소분류 2depth 계층 카테고리를 셀프 조인으로 구현하고 트리 형태로 반환한다.

**작업 내용**

`domain/category/entity/Category.java`
```java
@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parentCategory;   // null이면 대분류

    @OneToMany(mappedBy = "parentCategory")
    private List<Category> children = new ArrayList<>();

    @Column(nullable = false)
    private Integer displayOrder;

    @CreatedDate
    private LocalDateTime createdAt;
}
```

트리 조회 응답 예시:
```json
[
  {
    "id": 1,
    "name": "상의",
    "displayOrder": 1,
    "children": [
      { "id": 3, "name": "티셔츠", "displayOrder": 1 },
      { "id": 4, "name": "셔츠", "displayOrder": 2 }
    ]
  }
]
```

삭제 방어 로직:
- `children.isEmpty()` 로 하위 카테고리 존재 여부 검사
- 하위 카테고리 존재 시 `CATEGORY_HAS_CHILDREN` 예외 발생

**체크포인트**
- [ ] 대분류 생성 → 소분류 생성 → 트리 조회 정상 반환
- [ ] 하위 카테고리 있는 대분류 삭제 시 409 반환

---

### M. Product 도메인 구현

**목적**: 상품 CRUD, 페이징·필터·정렬, 재고 자동 상태 변경, soft delete를 구현한다.

**작업 내용**

`domain/product/entity/Product.java` 핵심 필드:
```java
@Entity
@Table(name = "products")
public class Product {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Lob
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ElementCollection
    @CollectionTable(name = "product_image_urls")
    private List<String> imageUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;   // ON_SALE, SOLD_OUT, HIDDEN

    // 재고 차감 (Order 생성 시 호출)
    public void decreaseStock(int quantity) {
        if (this.stockQuantity < quantity) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.stockQuantity -= quantity;
        if (this.stockQuantity == 0) {
            this.status = ProductStatus.SOLD_OUT;  // 자동 SOLD_OUT
        }
    }

    // 재고 복구 (Order 취소 시 호출)
    public void increaseStock(int quantity) {
        this.stockQuantity += quantity;
        if (this.status == ProductStatus.SOLD_OUT) {
            this.status = ProductStatus.ON_SALE;
        }
    }

    // Soft delete
    public void hide() {
        this.status = ProductStatus.HIDDEN;
    }
}
```

목록 조회 필터 (QueryDSL 또는 JPA Specification 활용):
- `categoryId` — 카테고리 필터
- `minPrice` / `maxPrice` — 가격 범위 필터
- `keyword` — 상품명 키워드 검색 (LIKE 쿼리)
- `sort` — `createdAt,desc` / `price,asc` 등 정렬

**체크포인트**
- [ ] 재고 0 등록 시 자동으로 `SOLD_OUT` 상태 확인
- [ ] 상품 삭제 시 실제 삭제가 아닌 `HIDDEN` 상태로 변경 확인
- [ ] 페이징 + 카테고리 필터 복합 쿼리 정상 동작

---

## Phase 4 — 구매 플로우

### N. Cart 도메인 구현

**목적**: 회원별 장바구니를 관리하고 동일 상품 수량 합산 및 재고 초과 방어 로직을 구현한다.

**작업 내용**

`domain/cart/entity/Cart.java`
```java
@Entity
@Table(name = "carts")
public class Cart {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> cartItems = new ArrayList<>();

    // 동일 상품(+옵션) 찾기
    public Optional<CartItem> findSameItem(Long productId, String size, String color) {
        return cartItems.stream()
                .filter(item -> item.getProduct().getId().equals(productId)
                        && item.getSize().equals(size)
                        && item.getColor().equals(color))
                .findFirst();
    }
}
```

`domain/cart/entity/CartItem.java`
```java
@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id")
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private String size;
    private String color;
    private int quantity;

    public void addQuantity(int quantity) {
        this.quantity += quantity;
    }

    public void changeQuantity(int quantity) {
        this.quantity = quantity;
    }
}
```

핵심 비즈니스 로직:
- 담기 시 `cart.findSameItem()` → 존재하면 수량 합산, 없으면 신규 추가
- 담기 전 `product.getStockQuantity() >= 요청수량` 검증 → 실패 시 `CART_QUANTITY_EXCEEDED`

**체크포인트**
- [ ] 동일 상품 재담기 시 수량 합산 확인
- [ ] 재고 초과 수량 담기 시 409 반환

---

### O. Order 도메인 구현

**목적**: 주문 생성 시 비관적 락으로 재고를 안전하게 차감하고, 가격 스냅샷 저장 및 상태 흐름을 관리한다.

**작업 내용**

`domain/order/entity/Order.java` 주요 구성:
- `orderItems` — 주문 항목 리스트 (product, size, color, quantity, **priceAtOrder** 스냅샷)
- `status` — `PENDING → PAID → SHIPPING → DELIVERED → CANCELLED`
- `shippingAddress` — 주문 시점 배송지 스냅샷

비관적 락 재고 차감:
```java
// ProductRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdWithLock(@Param("id") Long id);
```

주문 취소 정책:
- `PENDING` 상태 → 즉시 취소 가능
- `PAID` 상태 → 취소 가능 + 재고 복구
- `SHIPPING` 이후 → 회원이 직접 취소 불가 (`ORDER_CANCEL_NOT_ALLOWED`)

상태 변경 (ADMIN 전용):
```java
// ADMIN이 /api/admin/orders/{id}/status 로 상태 강제 변경
public void updateStatus(OrderStatus newStatus) {
    this.status = newStatus;
}
```

**체크포인트**
- [ ] 동시에 두 요청이 동일 상품 마지막 재고를 주문할 때 한 건만 성공 (비관적 락 검증)
- [ ] `priceAtOrder`에 주문 시점 가격이 저장되는지 확인 (상품 가격 변경 후에도 기존 주문 금액 유지)
- [ ] `SHIPPING` 상태 주문 취소 시도 시 409 반환

---

### P. Payment 도메인 구현

**목적**: 결제 요청 처리, 성공 시 Order 상태 전환, 실패 시 재고 복구, 환불 로직을 구현한다.

**작업 내용**

`domain/payment/entity/Payment.java`
```java
@Entity
@Table(name = "payments")
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)  // 1 Order : 1 Payment
    private Order order;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;  // CARD, TRANSFER

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;  // SUCCESS, FAILED, REFUNDED

    private LocalDateTime paidAt;
}
```

결제 처리 흐름:
```
결제 요청
  → Order 상태 PENDING 확인
  → 중복 결제 여부 확인 (PAYMENT_ALREADY_EXISTS)
  → 결제 처리 (외부 PG 연동 또는 Mock)
    ├─ 성공: Order status → PAID, paidAt 기록
    └─ 실패: 재고 복구, Payment status → FAILED
```

환불 처리:
- `Payment.status = REFUNDED`, `Order.status = CANCELLED`
- 재고 복구 (`product.increaseStock()`)

**참고**: 외부 PG사(포트원 등) 연동은 PRD 미결 사항. 현재는 Mock 처리 후 실제 연동으로 교체 가능하도록 `PaymentGateway` 인터페이스로 추상화 권장

**체크포인트**
- [ ] 결제 성공 후 Order status가 `PAID`로 변경되는지 확인
- [ ] 동일 주문 중복 결제 시도 시 409 반환
- [ ] 환불 후 재고 복구 확인

---

## Phase 5 — 리뷰

### Q. Review 도메인 구현

**목적**: DELIVERED 상태 주문에 한해 리뷰를 작성하고, 중복 방지 및 평균 평점 집계를 구현한다.

**작업 내용**

`domain/review/entity/Review.java`
```java
@Entity
@Table(name = "reviews")
public class Review {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    @Min(1) @Max(5)
    private Integer rating;

    @Column(nullable = false, length = 1000)
    private String content;

    @ElementCollection
    @CollectionTable(name = "review_image_urls")
    private List<String> imageUrls = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;
}
```

리뷰 작성 검증 로직:
```java
// 1. 주문 상태가 DELIVERED인지 확인
if (order.getStatus() != OrderStatus.DELIVERED) {
    throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
}

// 2. 동일 orderItem에 대한 리뷰 중복 작성 확인
if (reviewRepository.existsByUserAndOrder(user, order)) {
    throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
}
```

평균 평점 집계 — 상품 조회 응답에 포함:
```java
// ReviewRepository
@Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
Double findAverageRatingByProductId(@Param("productId") Long productId);
```

리뷰 수정/삭제 본인 확인:
```java
if (!review.getUser().getId().equals(currentUserId)) {
    throw new BusinessException(ErrorCode.FORBIDDEN);
}
```

**체크포인트**
- [ ] PENDING 주문으로 리뷰 작성 시도 시 403 반환
- [ ] 동일 주문 중복 리뷰 작성 시 409 반환
- [ ] 상품 상세 조회 시 `averageRating` 포함 확인

---

## Phase 6 — 마무리

### R. RBAC 권한 설정 완성

**목적**: 전 도메인 구현 완료 후 엔드포인트별 권한을 최종 점검하고 누락된 접근 제어를 완성한다.

**작업 내용**

| 도메인 | 비회원 | USER | ADMIN |
|--------|--------|------|-------|
| 상품 조회 | ✅ | ✅ | ✅ |
| 상품 CRUD | ❌ | ❌ | ✅ |
| 카테고리 조회 | ✅ | ✅ | ✅ |
| 카테고리 CRUD | ❌ | ❌ | ✅ |
| 장바구니 | ❌ | ✅ | ✅ |
| 주문 (본인) | ❌ | ✅ | ✅ |
| 주문 전체 조회 | ❌ | ❌ | ✅ |
| 결제 | ❌ | ✅ | ✅ |
| 리뷰 조회 | ✅ | ✅ | ✅ |
| 리뷰 작성/수정 | ❌ | ✅ (본인) | ✅ |
| 리뷰 삭제 | ❌ | ✅ (본인) | ✅ |

추가 작업:
- `@PreAuthorize("hasRole('ADMIN')")` 어노테이션으로 서비스 레이어 이중 방어
- 본인 리소스 접근 검증 (`review.getUser().getId().equals(currentUserId)`)
- 401/403 응답이 `ApiResponse` 포맷과 일치하는지 확인

**체크포인트**
- [ ] 권한 매트릭스 전 항목 Postman 테스트 통과
- [ ] 401, 403 응답 포맷이 `{ code, message, data }` 형식 준수

---

### S. 테스트 코드 작성

**목적**: 핵심 비즈니스 로직의 단위 테스트와 API 레이어의 통합 테스트를 작성하여 회귀를 방지한다.

**작업 내용**

단위 테스트 (Service 레이어):
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @InjectMocks UserService userService;

    @Test
    @DisplayName("이메일 중복 시 DUPLICATE_EMAIL 예외 발생")
    void signUp_DuplicateEmail_ThrowsException() {
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> userService.signUp(signUpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 사용 중인 이메일");
    }
}
```

통합 테스트 (Controller 레이어):
```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("로그인 성공 시 Access Token, Refresh Token 반환")
    void login_Success_ReturnsTokens() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(loginRequestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }
}
```

우선순위 테스트 항목:
- JWT 토큰 생성/검증/만료 (JwtProvider)
- 회원가입 이메일 중복 검증
- 재고 차감 및 SOLD_OUT 전환 (Product)
- 비관적 락 동작 (Order)
- 장바구니 수량 합산 (Cart)
- 리뷰 작성 조건 검증 (Review)

**체크포인트**
- [ ] `./gradlew test` 전체 통과
- [ ] 핵심 서비스 로직 단위 테스트 커버리지 확보

---

## Phase 7 — 배포

### T. Dockerfile 작성

**목적**: 멀티 스테이지 빌드로 최소 크기의 Docker 이미지를 생성하여 운영 환경에 배포 가능하게 한다.

**작업 내용**

`Dockerfile`
```dockerfile
# Stage 1: 빌드
FROM gradle:8.7-jdk21-alpine AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon   # 의존성 캐싱 레이어 분리
COPY src ./src
RUN gradle bootJar --no-daemon

# Stage 2: 실행
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 보안: root 계정이 아닌 전용 유저로 실행
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
```

`.dockerignore`
```
.gradle/
build/
.git/
*.md
src/test/
```

빌드 및 실행:
```bash
docker build -t clothing-store-api:latest .
docker run -d -p 8080:8080 \
  -e JWT_SECRET=your_secret \
  -e DB_URL=jdbc:mysql://... \
  -e DB_USERNAME=... \
  -e DB_PASSWORD=... \
  clothing-store-api:latest
```

**체크포인트**
- [ ] Docker 이미지 빌드 성공
- [ ] 컨테이너 기동 후 `/actuator/health` → `{ "status": "UP" }` 확인
- [ ] 이미지 크기 최소화 확인 (alpine 기반 JRE 이미지 사용)

---

### U. docker-compose.yml 작성

**목적**: 로컬 개발 및 단일 서버 배포 시 애플리케이션과 MySQL을 함께 관리한다.

**작업 내용**

`docker-compose.yml`
```yaml
version: "3.9"

services:
  db:
    image: mysql:8.0
    container_name: clothing-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
      MYSQL_DATABASE: clothing_store
      MYSQL_USER: ${DB_USERNAME}
      MYSQL_PASSWORD: ${DB_PASSWORD}
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - backend
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    image: clothing-store-api:latest
    container_name: clothing-api
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:mysql://db:3306/clothing_store?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      db:
        condition: service_healthy
    networks:
      - backend
    restart: unless-stopped

volumes:
  mysql-data:

networks:
  backend:
    driver: bridge
```

`.env` 파일 (`.gitignore`에 추가, 서버에만 존재)
```env
DB_ROOT_PASSWORD=rootpassword
DB_USERNAME=appuser
DB_PASSWORD=securepassword
JWT_SECRET=your-256-bit-secret-key-here
```

실행:
```bash
docker-compose up -d
docker-compose logs -f app    # 로그 확인
```

**체크포인트**
- [ ] `depends_on` + `healthcheck` 으로 DB 준비 전 앱 실행 방지 확인
- [ ] `mysql-data` 볼륨으로 컨테이너 재시작 후에도 데이터 유지 확인
- [ ] `.env` 파일이 Git에 포함되지 않음 확인

---

### V. 환경별 설정 분리 (prod 프로파일)

**목적**: 개발/운영 환경 설정을 명확히 분리하여 보안 취약점과 운영 사고를 예방한다.

**작업 내용**

운영 환경 체크리스트:

| 항목 | 개발(dev) | 운영(prod) |
|------|-----------|-----------|
| `ddl-auto` | `update` | `validate` 또는 `none` |
| `show-sql` | `true` | `false` |
| `log-level` | `DEBUG` | `WARN` |
| DB URL | localhost | 환경변수 |
| JWT secret | 고정 문자열 | 환경변수 |

`application-prod.yml`
```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

logging:
  level:
    root: WARN
    com.toyproject.backend: INFO
```

`/actuator/health` 활성화:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: when_authorized
```

**체크포인트**
- [ ] prod 프로파일로 실행 시 SQL 로그 미출력 확인
- [ ] 환경변수 미설정 시 앱 기동 실패 (빠른 실패) 확인

---

### W. CI/CD 파이프라인 구성 (GitHub Actions)

**목적**: 코드 Push 시 자동으로 빌드·테스트·이미지 빌드·배포가 실행되도록 파이프라인을 구성한다.

**작업 내용**

`.github/workflows/deploy.yml`
```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v4
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle.kts') }}

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build and test
        run: ./gradlew test bootJar --no-daemon
        env:
          JWT_SECRET: ${{ secrets.JWT_SECRET_TEST }}

      - name: Build Docker image
        if: github.ref == 'refs/heads/main'
        run: docker build -t ${{ secrets.DOCKERHUB_USERNAME }}/clothing-store-api:latest .

      - name: Push Docker image
        if: github.ref == 'refs/heads/main'
        run: |
          echo "${{ secrets.DOCKERHUB_TOKEN }}" | docker login -u "${{ secrets.DOCKERHUB_USERNAME }}" --password-stdin
          docker push ${{ secrets.DOCKERHUB_USERNAME }}/clothing-store-api:latest

  deploy:
    needs: build-and-test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Deploy to server via SSH
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SERVER_SSH_KEY }}
          script: |
            cd /opt/clothing-store
            docker-compose pull app
            docker-compose up -d --no-deps app
            docker image prune -f
```

GitHub Secrets 등록 목록:
- `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`
- `SERVER_HOST`, `SERVER_USER`, `SERVER_SSH_KEY`
- `JWT_SECRET_TEST` (테스트 전용)

**체크포인트**
- [ ] PR 생성 시 빌드/테스트 자동 실행
- [ ] `main` 브랜치 Push 시 자동 배포 완료
- [ ] Actions 탭에서 전체 파이프라인 초록불 확인

---

### X. 서버 배포 (EC2 / VPS)

**목적**: 클라우드 서버에 Docker 환경을 구성하고 Nginx 리버스 프록시 + HTTPS를 적용한다.

**작업 내용**

서버 초기 세팅 (Ubuntu 24.04 기준):
```bash
# Docker 설치
sudo apt update
sudo apt install -y docker.io docker-compose
sudo systemctl enable docker
sudo usermod -aG docker $USER

# 프로젝트 디렉터리 생성
mkdir -p /opt/clothing-store
cd /opt/clothing-store
# .env 파일 수동 생성 (절대 Git에 올리지 않음)
```

Nginx 리버스 프록시 설정 (`/etc/nginx/sites-available/clothing-store`):
```nginx
server {
    listen 80;
    server_name api.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name api.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/api.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

SSL 인증서 발급 (Let's Encrypt):
```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d api.yourdomain.com
```

방화벽 설정:
```bash
sudo ufw allow 22    # SSH
sudo ufw allow 80    # HTTP (HTTPS 리다이렉트용)
sudo ufw allow 443   # HTTPS
sudo ufw deny 8080   # Spring Boot 직접 접근 차단
sudo ufw enable
```

**체크포인트**
- [ ] `https://api.yourdomain.com/actuator/health` → `{ "status": "UP" }` 확인
- [ ] HTTP → HTTPS 리다이렉트 동작 확인
- [ ] 8080 포트 외부 직접 접근 차단 확인

---

### Y. 배포 후 최종 검증

**목적**: 배포된 운영 환경에서 전체 기능이 정상 동작하는지 E2E 테스트를 수행한다.

**작업 내용**

Postman Collection으로 다음 시나리오를 순서대로 테스트:

```
1. 회원가입 → 로그인 (Access Token, Refresh Token 수령)
2. 카테고리 생성 (ADMIN 토큰)
3. 상품 등록 (ADMIN 토큰)
4. 상품 목록 조회 (비회원)
5. 장바구니 담기 (USER 토큰)
6. 주문 생성
7. 결제 요청
8. 주문 상태 DELIVERED 변경 (ADMIN 토큰)
9. 리뷰 작성
10. Refresh Token으로 Access Token 재발급
11. 로그아웃 → Refresh Token 무효화 확인
```

헬스체크 엔드포인트:
```bash
curl https://api.yourdomain.com/actuator/health
# 예상 응답: { "status": "UP" }
```

**체크포인트**
- [ ] 전체 E2E 시나리오 통과
- [ ] 응답 포맷 `{ code, message, data }` 전 API 준수 확인
- [ ] 페이지네이션 응답 `totalElements`, `totalPages`, `currentPage` 포함 확인

---

### Z. 운영 모니터링 & 보안 점검

**목적**: 배포 이후 지속적인 운영을 위한 모니터링 체계와 보안 점검 항목을 수립한다.

**작업 내용**

로그 관리:
```yaml
# application-prod.yml 로그 설정
logging:
  file:
    name: /var/log/clothing-store/app.log
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30        # 30일치 보관
```

보안 점검 체크리스트:

| 항목 | 점검 내용 |
|------|-----------|
| JWT Secret | 256bit 이상 랜덤 문자열, 환경변수 주입 |
| HTTPS | 모든 API 요청 HTTPS 강제 |
| SQL Injection | JPA 파라미터 바인딩 사용 (Native Query 최소화) |
| XSS | 응답 Content-Type `application/json` 확인 |
| Rate Limiting | 로그인 API 무차별 대입 방어 (추후 구현) |
| 민감 정보 노출 | 에러 응답에 스택 트레이스 미포함 확인 |
| DB 백업 | MySQL 자동 백업 스케줄 설정 |

DB 백업 (cron):
```bash
# /etc/cron.d/mysql-backup
0 3 * * * root mysqldump -u root -p${DB_ROOT_PASSWORD} clothing_store > /backup/clothing_$(date +\%Y\%m\%d).sql
```

미결 사항 후속 대응 (PRD Section 7):
- [ ] Redis 도입 — Refresh Token 저장 및 인기 상품 캐싱
- [ ] S3 연동 — 상품/리뷰 이미지 저장
- [ ] 포트원 PG 연동 — 실제 결제 처리
- [ ] OAuth2 소셜 로그인 — 구글/카카오 연동
- [ ] Elasticsearch — 상품 검색 고도화

**체크포인트**
- [ ] 운영 서버 에러 로그 이상 없음 (24시간 모니터링)
- [ ] JWT Secret이 소스 코드/Git 어디에도 노출되지 않음 확인
- [ ] DB 백업 스크립트 실행 확인

---

## 진행 순서 체크리스트 요약

```
환경 설정
 ☐ A. 프로젝트 초기화 (Spring Initializr, 패키지 구조, Git)
 ☐ B. build.gradle.kts 의존성 설정
 ☐ C. application.yml 환경별 설정 (dev / prod 프로파일)

글로벌 기반
 ☐ D. ApiResponse 공통 응답 래퍼
 ☐ E. GlobalExceptionHandler + ErrorCode + BusinessException
 ☐ F. PageResponse 페이지네이션 공통 구조

인증/인가 (Phase 1 — 선행 필수)
 ☐ G. User 엔티티 / Repository / Role Enum
 ☐ H. SecurityFilterChain + UserDetailsService + 401/403 핸들러
 ☐ I. JwtProvider (Access/Refresh Token 생성·검증) + RefreshToken 엔티티
 ☐ J. JwtAuthenticationFilter (OncePerRequestFilter)
 ☐ K. AuthController + UserController (인증 API 전체)

카테고리/상품 (Phase 2)
 ☐ L. Category 도메인 (self-join, 트리 조회, 삭제 방어)
 ☐ M. Product 도메인 (페이징·필터·정렬, SOLD_OUT, soft delete)

구매 플로우 (Phase 3~4)
 ☐ N. Cart 도메인 (수량 합산, 재고 초과 방어)
 ☐ O. Order 도메인 (비관적 락, 가격 스냅샷, 상태 흐름)
 ☐ P. Payment 도메인 (결제 처리, Order 상태 전환, 환불)

리뷰 (Phase 5)
 ☐ Q. Review 도메인 (작성 조건 검증, 중복 방지, 평균 평점)

마무리
 ☐ R. RBAC 권한 설정 완성 + 401/403 응답 포맷 통일
 ☐ S. 테스트 코드 (단위 + 통합)

배포
 ☐ T. Dockerfile (멀티 스테이지 빌드)
 ☐ U. docker-compose.yml (app + MySQL + 볼륨 + 헬스체크)
 ☐ V. 환경별 설정 분리 (prod 프로파일 + actuator)
 ☐ W. GitHub Actions CI/CD 파이프라인
 ☐ X. 서버 배포 (Nginx 리버스 프록시 + Let's Encrypt SSL)
 ☐ Y. 배포 후 E2E 최종 검증 (Postman)
 ☐ Z. 운영 모니터링 + 보안 점검 + DB 백업
```
