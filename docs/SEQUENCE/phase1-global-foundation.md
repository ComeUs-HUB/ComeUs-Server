# Phase 1 — 글로벌 기반 (D~F)

> 상태: 구현 완료 — 모든 도메인이 공통으로 쓸 응답/예외/페이지네이션 구조가 이미 준비되어 있음

모든 API가 공유하는 응답 포맷, 예외 처리, 페이지네이션 구조를 만드는 단계. 이후 어떤 도메인을 구현하든 이 세 가지를 그대로 재사용하면 된다.

---

## D. 공통 응답 포맷 구현 (ApiResponse)

**목적**: 모든 API 응답을 일관된 구조(`code`, `message`, `data`)로 반환하는 공통 래퍼 클래스를 만든다.

**구현 현황 — 구현 완료**

- `global/response/ApiResponse.java`에 제네릭 `ApiResponse<T>`가 구현되어 있고, `success(data)`, `success(message, data)`, `error(code, message)` 정적 팩토리 메서드로 응답을 생성하는 방식이 확정되어 있음.
- `data`가 `null`인 경우도 응답 바디에 포함되도록 처리되어 있음. 새 컨트롤러를 작성할 때는 이 클래스를 그대로 재사용하면 되고, 별도의 응답 래퍼를 새로 만들 필요는 없음.

**개발 방향(참고)**
- 이후 각 도메인 컨트롤러는 반환 타입을 `ApiResponse<T>`로 통일 — 컨트롤러 시그니처를 새로 설계할 때 이 규칙만 지키면 된다.

**체크포인트**
- [x] 제네릭 타입 `T` JSON 직렬화 정상 동작
- [x] `data`가 `null`이어도 응답에 포함됨

---

## E. 전역 예외 처리 구현 (GlobalExceptionHandler)

**목적**: 도메인별 비즈니스 예외를 계층적으로 정의하고, 전역에서 일관되게 처리한다.

**구현 현황 — 구현 완료**

- `global/exception/ErrorCode.java`에 Common/Auth/Product/Category/Cart/Order/Payment/Review 전 도메인의 에러코드가 이미 정의되어 있음 — 아직 해당 도메인이 구현되지 않았음에도 에러코드 체계는 미리 확정된 상태.
- `BusinessException`(에러코드를 감싸는 런타임 예외)과 `GlobalExceptionHandler`(`@RestControllerAdvice`)가 구현되어 `BusinessException`, `MethodArgumentNotValidException`, 그 외 미처리 `Exception` 세 가지를 각각 처리 중.
- 도메인별 개별 예외 클래스(예: `ProductNotFoundException`처럼 `BusinessException`을 상속하는 구체 클래스)는 아직 없음 — 해당 도메인 패키지가 생성되는 시점에 함께 추가하면 됨.

**개발 방향**
- 새 도메인을 구현할 때 이미 `ErrorCode`에 정의된 항목을 그대로 사용하고, 필요하면 그 도메인의 `exception` 하위 폴더에 `BusinessException`을 상속하는 구체 예외 클래스를 추가하는 패턴을 유지.
- `ErrorCode`에 없는 새로운 실패 케이스가 생기면 기존 명명 규칙(도메인 접두사 + 일련번호, 예: `P00X`, `O00X`)을 따라 추가.

**체크포인트**
- [x] 존재하지 않는 리소스 요청 시 404 + 커스텀 메시지 반환
- [x] `@Valid` 실패 시 400 응답 및 필드 오류 메시지 반환
- [ ] 도메인별 구체 예외 클래스 추가 (각 도메인 구현 시점에)

---

## F. 페이지네이션 공통 구조 구현

**목적**: 목록 조회 API에서 일관된 페이지네이션 응답(`totalElements`, `totalPages`, `currentPage` 등)을 제공한다.

**구현 현황 — 구현 완료 (미사용 상태)**

- `global/response/PageResponse.java`에 `content`, `totalElements`, `totalPages`, `currentPage`, `pageSize`, `hasNext`, `hasPrevious`를 담는 구조와 `PageResponse.of(Page<T>)` 팩토리 메서드가 이미 구현되어 있음.
- 다만 이를 실제로 사용하는 컨트롤러가 아직 하나도 없음 — 목록 조회 API를 만드는 도메인(Product, Order, Review 등)에서 `Pageable` 파라미터를 받아 이 클래스로 감싸 반환하면 됨.

**개발 방향**
- 목록 조회 엔드포인트를 새로 만들 때는 매번 페이지네이션 구조를 새로 설계하지 말고 `PageResponse.of()`를 그대로 재사용.
- 정렬/필터 파라미터(`sort`, `size` 등)의 기본값과 최대 `size` 제한은 도메인별로 필요 시 결정.

**체크포인트**
- [x] `PageResponse` 클래스 자체 구현 완료
- [ ] 실제 목록 조회 API에서 `PageResponse` 사용 (Product 목록 조회 등에서 처음 적용될 예정)
