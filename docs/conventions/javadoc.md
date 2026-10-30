# Javadoc 규칙

## 왜 정하는가

이 저장소는 주석이 코드의 **0.74배**다 — 주석 2,510줄 대 코드 3,408줄. 처음 쟀을 때의 1.20배에서
내려온 것은 cart를 이 규칙대로 새로 써서 비율이 희석된 결과이고, **먼저 쓴 파일은 그대로 남아 있다.**

```
4.7  KakaoLoginResult     19줄 주석 /  4줄 코드
3.3  JwtProperties        40줄 주석 / 12줄 코드
2.8  CategoryRepository   51줄 주석 / 18줄 코드
2.6  ApiResponse          69줄 주석 / 26줄 코드
```

길면 읽지 않고, **읽지 않는 주석은 낡는다.** 낡은 주석은 없는 것보다 나쁘다.

## 원칙 셋

1. **한 문장으로 끝낼 수 있으면 거기서 끝낸다.**
2. **코드를 읽으면 아는 것은 쓰지 않는다.** `getName()`에 "이름을 반환한다"를 붙이지 않는다.
3. **왜는 한 줄, 근거는 링크.** 배제한 대안이 있으면 [decisions/](../decisions/)에 쓰고 링크만 남긴다.

### 주석보다 코드를 먼저 고친다

주석을 쓰고 싶어지면 아래 순서로 시도한다. **3번까지 내려온 것만 주석이 된다.**

1. 변수·메서드 이름을 고쳐서 주석이 필요 없게 만든다.
2. 메서드를 추출해서 이름으로 설명한다.
3. 그래도 남는 "왜"만 주석으로 쓴다.

## 분량 상한

| 대상 | 상한 |
|---|---|
| 클래스·record·enum | 첫 문장 1개 + **본문 5줄 이내** |
| 메서드 | **1줄**. 태그는 자명하지 않은 것만 |
| 필드·enum 상수 | 1줄 또는 생략 |
| 인라인 `//` | **2줄 이내** |

판단 기준은 하나다 — **스크롤 없이 한눈에 안 들어오면 이미 너무 길다.**

상한을 넘길 만한 내용이면 주석을 줄이는 게 아니라 **내용을 밖으로 옮긴다.** 아래 표를 먼저 본다.

## 무엇을 어디에 쓰나

| 내용 | 주인 |
|---|---|
| 이 타입이 무엇인가 | Javadoc 첫 문장 |
| **왜 이렇게 설계했나 · 배제한 대안** | [decisions/](../decisions/) — Javadoc은 링크만 |
| API 계약 (엔드포인트·권한·응답) | [domains/](../domains/) |
| 에러 코드의 의미 | [error-codes.md](error-codes.md) |
| 진행 상태 | [roadmap.md](../roadmap.md) |
| **아직 정하지 못한 것** | 이슈 — 코드에는 `// TODO(#42): ...` 한 줄만 |
| 한 줄짜리 구현 주의점 | 그 줄 위의 `//` 주석 |

[docs/README.md](../README.md)의 "같은 사실이 두 파일에 있으면 하나는 반드시 낡는다"가 주석에도 적용된다.

## 첫 문장

첫 문장은 IDE 툴팁과 색인에 노출되는 **유일한 부분**이다. 여기서 끝나도 의미가 통해야 한다.

- 명사구나 3인칭 동사구로 쓴다 — `JWT subject로 회원을 조회해 UserPrincipal로 변환한다.`
- 타입 이름을 되풀이하지 않는다. `CartService`에 "장바구니 서비스"는 정보가 없다.
- 섹션 헤더로 열지 않는다. **섹션이 필요할 만큼 길다는 것은 상한을 넘었다는 신호다.**

## 반드시 쓰는 것

아래는 짧게라도 남긴다. **주석이 없으면 다음 사람이 "불필요한 코드"로 보고 지운다.**

| 경우 | 이 저장소의 예 |
|---|---|
| 관용적이지 않은 방어 로직 | `// int로 더하면 기존 수량 + Integer.MAX_VALUE가 음수로 넘쳐 재고 검사를 통과한다` |
| 순서에 의미가 있는 호출 | `// 상품을 먼저 읽어야 P001일 때 빈 장바구니 행이 남지 않는다` |
| 의도적으로 비싼 선택 | 매 요청 DB 조회, 캐싱하지 않음 — "최적화하고 싶어지는" 지점 |
| 환경 차이 우회 | `// V4에만 두면 H2(@DataJpaTest)에서 재현되지 않아 @Table에도 선언한다` |
| 예외를 구분해 던지는 이유 | 합치면 상위 계층이 분기 근거를 잃는 경우 |
| public API의 계약 | 발생 예외와 대응 에러 코드 (`@throws`) |

주석을 줄이는 작업에서 **이 표에 걸리는 주석은 손대지 않는다.**

## 쓰지 않는 것

- **섹션 헤더** — `<b>역할</b>`, `<b>설계</b>`, `<b>주의</b>`. 틀이 분량을 강제한다
- **HTML 치장** — `<b>`, `<br>`, `<ul><li>`, `<ol>`, `<table>`, `<pre>`.
  **`<p>`만 예외**다(문단 구분에 필요). 강조가 필요하면 문장을 짧게 쓴다. `{@code}`·`{@link}`는 허용
- **이모지** — `⚠️`를 포함해 쓰지 않는다. 전부 경고면 아무것도 경고가 아니다
- **대화체** — "~인데 ~라서 ~하기 때문이다"로 이어지는 문장
- **서사** — 다른 클래스와 비교하며 설계 경위를 설명하는 문단
- **자문자답** — "왜 A가 아닌가?" 같은 소제목
- **ADR·계약 문서 내용 복사** — 링크로 대신한다
- **문서 행 번호 참조** — `문서 173행`. 문서가 바뀌는 순간 거짓이 된다. 참조는 **파일 경로까지만** 쓴다
- **미해결 결정을 서술로 적기** — "결정 필요 — A와 B 중…" 같은 토론은 이슈에 적고,
  코드에는 `// TODO(#42): 7단계에서 UserService로 이동 여부 결정` 한 줄만 남긴다
- **주석 처리된 코드** — 커밋하지 않고 삭제한다. 히스토리는 Git이 기억한다

## 예시

### 1. 섹션 헤더와 HTML을 걷어낸다

`ProductStatus` — 29줄에서 4줄로.

**Before**

```java
/**
 * [Phase 3 · 5단계] 상품 상태
 *
 * <p><b>역할</b><br>
 * 상품이 팔리는 중인지, 재고가 비었는지, 지워졌는지를 한 값으로 표현한다.
 *
 * <p><b>축이 둘이다</b><br>
 * {@code ON_SALE} ↔ {@code SOLD_OUT}은 <b>재고 축</b>이고 {@code HIDDEN}은 <b>삭제 축</b>이다.
 * 한 필드에 담겨 있지만 서로 독립이라, 재고가 찬다고 해서 {@code HIDDEN}이 풀리지 않는다
 * ({@link Product#increaseStock(int)} 참고). 둘을 같은 판정으로 뭉개면
 * <b>삭제된 상품이 재고 복구만으로 목록에 다시 뜬다.</b>
 * ... (이하 주의 2항목, 상수별 설명 3개)
 */
```

**After**

```java
/**
 * [Phase 3 · 5단계] 상품 상태.
 *
 * <p>재고 축({@code ON_SALE} ↔ {@code SOLD_OUT})과 삭제 축({@code HIDDEN})은 독립이다 —
 * 재고가 차도 {@code HIDDEN}은 풀리지 않는다({@link Product#increaseStock(int)}).
 */
```

지운 것은 사라지지 않았다. "왜 두 축을 나눴나"는 [ADR 0013](../decisions/catalog/0013-hide-soft-deleted-products-everywhere.md)에,
상수별 의미는 이름 자체에 있다.

### 2. 남길 것을 남기고 옮길 것을 옮긴다

`CustomUserDetailsService` — 88줄 중 주석 50줄. 섹션 헤더 3개, 죽은 문서의 행 번호 참조 3개,
미해결 결정 토론 15줄이 들어 있고, **정작 핵심(예외를 구분해 던져야 한다)이 장식 사이에 묻혀 있다.**

**After**

```java
/**
 * [Phase 2 · 6단계] JWT subject(userId)로 회원을 조회해 {@link UserPrincipal}로 변환한다.
 *
 * <p>매 요청 DB를 조회한다. claim만 믿으면 탈퇴 회원이 토큰 만료까지 통과하므로 캐싱하지 않는다.
 * <p>미존재와 탈퇴를 다른 예외로 던진다. 합치면 필터가 A001(401)과 A008(403)을 가르지 못한다.
 * <p>그 구분과 {@code UserRepository} 직접 주입 근거는 ADR 0021.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * @throws UsernameNotFoundException 회원이 없거나 subject가 숫자가 아닐 때 (A001)
     * @throws DisabledException         탈퇴한 회원일 때 (A008)
     */
    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        ...
    }

    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            // 서명을 통과한 토큰만 도달하므로 사실상 발생하지 않지만,
            // 그대로 새어 나가면 500이 된다. 인증 실패로 바꿔 A001로 응답한다.
            throw new UsernameNotFoundException("잘못된 형식의 subject입니다: " + userId, e);
        }
    }
}
```

주석 50줄 → 9줄. 두 문단은 "[반드시 쓰는 것](#반드시-쓰는-것)"의 *의도적으로 비싼 선택*과
*예외를 구분해 던지는 이유*에 해당해 **지우지 않고 한 줄로 압축**했고, `parseUserId`의 인라인 주석도
같은 이유로 남겼다.

`UserPrincipal.isEnabled()`에 탈퇴 판정을 맡기지 않은 이유와 `UserRepository` 직접 주입 근거는
[ADR 0021](../decisions/platform/0021-user-repository-in-security.md)로 옮겼다.
그 자리에 있던 "결정 필요" 15줄과 삭제된 문서의 행 번호 참조 3개가 함께 사라졌다.

## 태그

| 태그 | 쓸 때 |
|---|---|
| `@param` | **이름에서 드러나지 않는 제약**이 있을 때만 (`null` 허용, 단위, 범위) |
| `@return` | 첫 문장에 이미 있으면 **생략** |
| `@throws` | `BusinessException`은 **항상** 쓰고 에러 코드를 함께 적는다 |
| `{@link}` | 타입·메서드 참조 |
| `{@code}` | 값·식별자·아직 없는 타입 |

순서는 `@param` → `@return` → `@throws` → `@deprecated`.
**이름만 되풀이할 거면 태그 자체를 쓰지 않는다** — `@param userId 회원 id`는 정보가 없다.

### 문서 참조 형식

ADR은 **번호만** 쓴다. 번호에서 경로를 찾는 일은 [decisions/README.md](../decisions/README.md)의
색인 표가 한다 — 주석에 경로를 적으면 ADR 파일명이 바뀔 때 같이 낡고, 줄폭 80자도 넘긴다.
Javadoc에 마크다운 링크(`[ADR 0016](...)`)를 쓰지 않는다 — 렌더링되지 않고 문자 그대로 나온다.

```java
 * <p>... 재고를 점유하지 않는다(ADR 0016).
```

계약 문서는 `{@code docs/domains/purchase/cart.md}` 처럼 **경로를 그대로** 쓴다.

### 단계 표기

`[Phase 4 · 2단계]` 형식은 **유지한다.** [roadmap.md](../roadmap.md)의 단계 표와 1:1로 대응하므로
거짓이 되지 않고, 한 줄로 소속을 보여준다.

task 알파벳(`[7단계 · I·K]`, `[6단계 · J]`, `[4단계 · G]`)은 **폐기한다.** 더 이상 추적되지 않는
식별자라 독자가 확인할 방법이 없다. Phase 2의 단계 번호는 roadmap의 표와 이미 일치하므로
`[N단계 · X]` → `[Phase 2 · N단계]`로 옮기면 된다. 단계를 나누지 않은 Phase 1은 `[Phase 1]`만 쓴다.

> 표기를 옮기는 커밋에서 [roadmap.md](../roadmap.md)의 Phase 설명문도 함께 고친다.
> 두 곳이 어긋나면 둘 중 하나는 거짓이다.

## 형식

- 주석은 한국어로 쓰고, 종결은 `~한다` 평서형으로 통일한다.
- Javadoc은 **80자 내외에서 줄바꿈**한다.
- 블록 주석은 주변 코드와 같은 레벨로 들여쓴다.

## 작성 전 자가 점검

1. 이 주석을 지우면 독자가 실제로 모르게 되는 정보가 있는가? 없으면 지운다.
2. 이름을 고쳐서 없앨 수 있는 주석인가?
3. 상한을 넘는가? 넘으면 `docs/`로 옮기고 링크만 남긴다.
4. 지금 코드 기준으로 전부 사실인가?

## 확인

모두 저장소 루트에서 실행한다.

```bash
# 클래스 Javadoc 본문이 5줄을 넘는 파일
# 블록 전체 길이로 재면 @param이 많은 record가 거짓 양성이 된다 — 요약 문단과 태그를 빼고 센다
python3 - << 'EOF'
import re
from pathlib import Path
for f in sorted(Path("src/main/java").rglob("*.java")):
    m = re.search(r"^/\*\*\n((?: \*.*\n)*?) \*/\n", f.read_text(encoding="utf-8"), re.M)
    if not m:
        continue
    ls = [l[2:].lstrip() if len(l) > 2 else "" for l in m.group(1).splitlines()]
    ls = ls[:next((i for i, l in enumerate(ls) if l.startswith("@")), len(ls))]
    paras, cur = [], []
    for l in ls:
        if l:
            cur.append(l)
        elif cur:
            paras.append(cur); cur = []
    if cur:
        paras.append(cur)
    body = sum(len(p) for p in paras[1:])
    if body > 5:
        print(f"{f}: 본문 {body}줄")
EOF

# HTML 치장 (<p>는 허용)
grep -rnoE '<(b|br|ul|ol|li|table|tr|th|td|pre)>' src/main/java --include="*.java"

# 이모지 (0이어야 한다)
grep -rn "⚠️" src/main/java --include="*.java"

# 80자를 넘는 Javadoc 줄
# awk의 length()는 macOS에서 바이트를 세므로 한글에서 거짓 양성이 난다 — 글자 수로 센다
python3 - << 'EOF'
import re
from pathlib import Path
for f in sorted(Path("src/main/java").rglob("*.java")):
    for i, l in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
        if re.match(r"^[ \t]*\*", l) and len(l) > 80:
            print(f"{f}:{i}  {len(l)}자")
EOF

# 주석이 코드의 2배를 넘는 파일
# 선언만 있는 record·enum은 코드가 몇 줄뿐이라 여기 걸린다 — 위 상한을 지켰다면 무시한다
for f in $(find src/main/java -name "*.java"); do
  c=$(grep -cE '^\s*(/\*\*|\*|\*/|//)' "$f"); t=$(wc -l < "$f"); code=$((t - c))
  [ "$code" -gt 3 ] && [ "$c" -gt $((code * 2)) ] && echo "$f  주석 $c / 코드 $code"
done

# 주석이 가리키는 문서가 실존하는지
grep -rhoE '[A-Za-z0-9_/.-]+\.md' src/main/java --include="*.java" | sort -u |
  while read -r p; do [ -e "docs/$p" ] || [ -e "$p" ] || echo "DEAD $p"; done
```

## 기존 코드

**규칙을 소급 적용하는 전용 브랜치에서 일괄 정리한다.** 애초에는 "다른 이유로 그 파일을 고칠 때
함께 줄인다"였는데, 그 방식으로는 Phase 1·2 코드가 손대지 않는 동안 계속 남았다. 비율이 나쁜
`global`(0.77) · `auth`(1.17) · `user`(1.00) · `category`(0.90)가 전부 그때 쓴 코드다.

- 주석만 바꾸는 커밋은 **동작을 하나도 바꾸지 않아야 한다.** 코드 변경이 섞이면 같은 커밋에 두지 않는다.
- 커밋은 패키지 단위로 쪼갠다 — diff가 커서 한 덩어리로는 리뷰가 되지 않는다.
- 축소 중에 "[반드시 쓰는 것](#반드시-쓰는-것)"에 해당하는 내용을 만나면 **지우지 말고 한 줄로 압축**한다.
- ADR로 옮길 내용이 있으면 **ADR을 먼저 쓴 커밋**을 올리고, 그 다음 커밋에서 주석을 링크로 바꾼다.

`docs/` 링크가 깨졌거나 내용이 **거짓이 된** 주석은 이 작업과 무관하게 즉시 고친다.
