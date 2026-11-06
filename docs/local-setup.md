# 로컬 실행

**필요한 것**: Java 21, MySQL 8

## 1. 데이터베이스

```sql
CREATE DATABASE toyproject CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

스키마는 만들지 않아도 된다. **Flyway가 기동 시 `V1__init.sql`로 생성한다**
([ADR 0008](decisions/platform/0008-flyway-owns-schema.md)).

## 2. `.env` — 프로젝트 루트

`application.yml`이 `spring.config.import`로 읽는다. **`.gitignore` 대상이라 클론 후 직접 만들어야 한다.**

```properties
LOCAL_DB_USERNAME=<mysql 사용자>
LOCAL_DB_PASSWORD=<mysql 비밀번호>

KAKAO_CLIENT_ID=<카카오 REST API 키>
KAKAO_CLIENT_SECRET=<카카오 client secret>
KAKAO_ADMIN_KEY=<카카오 Admin 키>

# 최소 32바이트. 짧으면 기동 시 WeakKeyException.
# 키 길이가 알고리즘을 정한다 — 32+ HS256, 48+ HS384, 64+ HS512
JWT_SECRET=<32바이트 이상 문자열>
```

## 3. `src/main/resources/application-local.yml`

이것도 **`.gitignore` 대상이라 직접 만들어야 한다.**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/toyproject?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: ${LOCAL_DB_USERNAME}
    password: ${LOCAL_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

kakao:
  client-id: ${KAKAO_CLIENT_ID}
  client-secret: ${KAKAO_CLIENT_SECRET}
  admin-key: ${KAKAO_ADMIN_KEY}
  # BE 콜백 주소다. FE 주소가 아니다
  redirect-uri: http://localhost:8080/api/auth/kakao/callback

app:
  frontend:
    base-url: http://localhost:3000
  cors:
    allowed-origins: http://localhost:3000
  cookie:
    # 평문 HTTP에서 Safari가 Secure 쿠키를 거부해 로그인이 실패한다. local은 반드시 false
    secure: false
```

## 4. 카카오 개발자 콘솔

Redirect URI에 **`http://localhost:8080/api/auth/kakao/callback`** 을 등록한다.
누락하면 카카오가 `KOE006`으로 거부해 로그인이 첫 시도부터 실패한다.

## 5. 실행

```bash
./gradlew bootRun     # http://localhost:8080
./gradlew test        # 전체 테스트
```

테스트는 H2 인메모리로 돌기 때문에 MySQL이 없어도 통과한다.
현재 테스트 규모는 [roadmap.md](roadmap.md)에 있다.
