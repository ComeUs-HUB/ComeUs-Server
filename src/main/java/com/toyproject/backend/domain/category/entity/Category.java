package com.toyproject.backend.domain.category.entity;

import com.toyproject.backend.domain.category.exception.CategoryErrorCode;
import com.toyproject.backend.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * [Phase 3 · 2단계] 카테고리 엔티티
 *
 * <p><b>역할</b><br>
 * 상품 분류의 <b>대분류 &gt; 소분류</b> 2단계 트리. self-join으로 표현한다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>Setter를 만들지 않는다. {@code User}와 같은 규칙이다 — 상태 변경은 아래 도메인 메서드로만 열린다.</li>
 *   <li>{@code updatedAt}을 두지 않는다. 계약({@code docs/domains/catalog/category.md})에 없고,
 *       카테고리는 수정 이력을 추적할 대상이 아니다. 그래서 {@code @LastModifiedDate}도 없다.</li>
 *   <li><b>{@code @OneToMany children}을 두지 않는다.</b> 트리 조립은 서비스가 전체를 한 번에 읽어
 *       메모리에서 부모 id로 묶는다. 컬렉션을 매핑하면 N+1을 부르거나 {@code @BatchSize} 같은
 *       추가 장치가 필요해지는데, 2depth에서는 얻는 것이 없다.</li>
 * </ul>
 *
 * <p><b>깊이 제한 — 생성 지점 하나가 막는다</b><br>
 * 테이블은 self-join이라 <b>구조적으로는 무한히 깊어질 수 있다.</b> "2depth"는 스키마가 아니라
 * 규칙이므로 코드가 강제해야 한다. 그 지점을 {@link #child(Category, String, int)} <b>하나로 모았다</b> —
 * 서비스 곳곳에 {@code if}를 흩뿌리는 대신 생성자가 불변식을 지키게 하는 방식이며,
 * {@code ApiResponse}가 compact constructor로 배타성을 강제하는 것과 같은 접근이다.
 *
 * <p><b>부모 변경(reparenting)을 지원하지 않는 이유</b><br>
 * {@link #updateInfo(String, int)}는 이름과 전시 순서만 바꾼다. 부모를 옮기려면 검증이
 * <b>양쪽</b>으로 갈라진다 — "새 부모가 대분류인가"는 이 엔티티가 알지만,
 * "내가 자식을 갖고 있지 않은가"(자식 있는 대분류가 소분류로 내려가면 손자가 생긴다)는
 * 컬렉션이 없어 알 수 없고 Repository를 봐야 한다. 계약에 없는 기능을 위해 불변식을 두 곳으로
 * 쪼갤 이유가 없다. 필요해지면 그때 서비스에 명시적인 이동 유스케이스로 추가한다.
 *
 * <p><b>주의</b><br>
 * 이름 중복을 막는 유니크 제약을 <b>일부러 두지 않았다.</b> 계약에 없기도 하지만,
 * {@code (parent_id, name)}으로 걸어도 <b>대분류끼리는 걸리지 않는다</b> —
 * MySQL은 UNIQUE 컬럼의 NULL 중복을 허용하므로 {@code parent_id IS NULL}인 행들은 서로 충돌하지 않는다
 * ({@code User.email}이 nullable+unique로 성립하는 것과 같은 성질이다).
 * 절반만 동작하는 제약을 두느니 없는 편이 낫다. 필요해지면 서비스에서 검사한다.
 */
@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 상위 카테고리. 대분류면 {@code null}이다.
     *
     * <p>LAZY로 두어도 <b>{@code getParent().getId()}는 프록시를 초기화하지 않는다</b>(식별자는
     * 프록시가 이미 들고 있다). 트리 조립이 추가 쿼리 없이 부모 id로 묶을 수 있는 이유다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Category(Category parent, String name, int displayOrder) {
        this.parent = parent;
        this.name = name;
        this.displayOrder = displayOrder;
    }

    /** 대분류 생성. {@code parent}가 없는 최상위 노드다. */
    public static Category root(String name, int displayOrder) {
        return new Category(null, name, displayOrder);
    }

    /**
     * 소분류 생성.
     *
     * <p><b>2depth 규칙이 강제되는 유일한 지점이다.</b> {@code parent}가 이미 소분류이면
     * 3depth가 되므로 거부한다.
     *
     * @param parent 상위 대분류. {@code null}이면 {@link #root(String, int)}를 쓴다
     * @throws BusinessException {@code parent}가 대분류가 아니면 CA003(400)
     */
    public static Category child(Category parent, String name, int displayOrder) {
        if (!parent.isRoot()) {
            throw new BusinessException(CategoryErrorCode.CATEGORY_DEPTH_EXCEEDED);
        }
        return new Category(parent, name, displayOrder);
    }

    /**
     * 이름과 전시 순서를 바꾼다. <b>부모는 바꾸지 않는다</b>(클래스 Javadoc 참고).
     *
     * <p>{@code User.updateProfile}과 달리 "null이면 변경하지 않음" 규약을 쓰지 않는다.
     * 두 필드 모두 필수값이라 비워 둘 수 있는 상태가 없고, 요청 DTO가 {@code @NotBlank}로
     * 이미 걸러 준다.
     */
    public void updateInfo(String name, int displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
    }

    /** 대분류이면 {@code true}. 트리의 뿌리이자, 소분류를 받을 수 있는 유일한 자리다. */
    public boolean isRoot() {
        return this.parent == null;
    }
}
