package com.toyproject.backend.global.response;

/**
 * [1단계 · D] 공통 응답 래퍼
 *
 * <p><b>역할</b><br>
 * 모든 API 응답을 하나의 스키마로 감싼다. 컨트롤러는 {@code ResponseEntity<ApiResponse<T>>}를 반환하고,
 * 예외는 {@code GlobalExceptionHandler}가, 401·403은 {@code JwtAuthenticationEntryPoint}가
 * 각각 같은 포맷으로 만든다.
 *
 * <pre>
 * 성공  { "success": true,  "data": { ... }, "error": null }
 * 실패  { "success": false, "data": null,    "error": { "code": "A007", ... } }
 * </pre>
 *
 * <p><b>불변식</b> — {@code success}는 사실상 파생값이다 ({@code success == (error == null)}).
 * <ul>
 *   <li>{@code data}와 {@code error}는 <b>배타적</b>이다. 동시에 값을 가질 수 없다.</li>
 *   <li>성공에 {@code error}가 있을 수 없고, 실패에 {@code error}가 없을 수 없다.</li>
 *   <li>⚠️ <b>{@code (true, null, null)}은 유효하다</b> — 데이터 없는 성공이다.
 *       {@code POST /api/auth/logout}과 {@code DELETE /api/users/me}가 여기 해당한다.
 *       불변식을 "정확히 하나만 non-null"로 조이면 <b>이 둘이 500으로 깨진다.</b></li>
 * </ul>
 *
 * <p><b>왜 compact constructor에서 검증하는가</b><br>
 * record의 canonical constructor는 <b>record보다 접근성을 낮출 수 없다</b>. {@code public record}면
 * 생성자도 public이 강제되므로, {@code private}으로 숨겨 정적 팩터리 사용을 강제하는 우회가 불가능하다.
 * 위 불변식을 코드로 지키는 유일한 수단이 여기서 거부하는 것이다.
 *
 * <p>{@link IllegalArgumentException}을 던진다. 도메인 실패가 아니라 <b>프로그래밍 오류</b>이므로
 * {@code BusinessException}으로 감싸지 않는다 — {@code GlobalExceptionHandler}의
 * fallback({@code C003}, 500 + {@code log.error})으로 떨어지는 것이 맞다.
 *
 * <p><b>주의</b>
 * <ul>
 *   <li><b>인자 없는 {@code success()} 정적 팩터리는 만들 수 없다.</b> record 컴포넌트
 *       {@code success}(boolean)의 자동 생성 접근자 {@code success()}와 시그니처가 충돌해
 *       컴파일 에러가 난다(static/instance 무관). 데이터 없는 성공은 {@code success(null)}로 만든다.</li>
 *   <li>{@code GET /api/auth/kakao/authorize}와 {@code /callback}은 <b>이 포맷을 쓰지 않는다.</b>
 *       항상 302이며, 실패도 예외를 던지지 않고 {@code {FE}/oauth/complete?error={code}}로
 *       리다이렉트한다 — 브라우저 네비게이션이라 JSON이 사용자 화면에 노출되면 안 된다.</li>
 * </ul>
 *
 * @param <T>     성공 응답 본문의 타입. 데이터 없는 성공은 {@code Void}를 쓴다
 * @param success 성공 여부. {@code error}의 부재와 항상 일치한다
 * @param data    성공 시 본문. 데이터 없는 성공이면 {@code null}
 * @param error   실패 시 오류 정보. 성공이면 {@code null}
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    /**
     * <b>배타성({@code data}·{@code error} 동시 non-null 금지)은 별도 조건문 없이 성립한다</b> —
     * 성공이면 첫 검사가 {@code error}를, 실패면 셋째 검사가 {@code data}를 막기 때문에
     * 어느 분기로도 둘이 함께 존재할 수 없다.
     */
    public ApiResponse {
        if (success && error != null) {
            throw new IllegalArgumentException("success 응답에는 error가 있을 수 없습니다.");
        }
        if (!success && error == null) {
            throw new IllegalArgumentException("실패 응답에는 error가 반드시 있어야 합니다.");
        }
        if (!success && data != null) {
            throw new IllegalArgumentException("실패 응답에는 data가 있을 수 없습니다.");
        }
    }

    /**
     * 성공 응답.
     *
     * @param <T>  본문 타입
     * @param data 본문. <b>데이터 없는 성공이면 {@code null}을 넘긴다</b>
     *             (삭제·로그아웃 등. 204를 쓰지 않으므로 바디는 항상 있다)
     * @return {@code success=true}이고 {@code error}가 없는 응답
     */
    public static <T> ApiResponse<T> success(T data) {

        return new ApiResponse<>(true, data, null);
    }

    /**
     * 실패 응답.
     *
     * <p>반환 타입이 {@code ApiResponse<Void>}인 것은 실패 시 {@code data}가 항상 {@code null}이기 때문이다.
     *
     * @param error 오류 정보. <b>{@code null}이면 거부된다</b> — {@code error} 없는 실패 응답은
     *              클라이언트가 {@code error.code}를 읽을 수 없어 성립하지 않는다
     * @return {@code success=false}이고 {@code data}가 없는 응답
     * @throws IllegalArgumentException {@code error}가 {@code null}인 경우
     */
    public static ApiResponse<Void> error(ErrorResponse error) {

        return new ApiResponse<>(false, null, error);
    }
}
