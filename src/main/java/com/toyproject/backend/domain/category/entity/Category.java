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
 * [Phase 3 · 2단계] 대분류 &gt; 소분류 2단계 트리. self-join으로 표현한다.
 *
 * <p>테이블은 구조적으로 무한히 깊어질 수 있어 2depth는 코드가 강제한다 — 그 지점을
 * {@link #child(Category, String, int)} 하나로 모았다.
 * <p>부모 변경을 지원하지 않는다 — 검증이 엔티티와 Repository 양쪽으로 갈라진다.
 * <p>{@code @OneToMany children}을 두지 않는다. 트리는 서비스가 전체를 읽어 메모리에서 묶는다.
 * <p>이름 유니크 제약을 두지 않는다 — NULL 중복이 허용돼 대분류끼리는 걸리지 않는다.
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
     * <p>LAZY여도 {@code getParent().getId()}는 프록시를 초기화하지 않는다 — 트리 조립이
     * 추가 쿼리 없이 부모 id로 묶을 수 있는 이유다.
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
     * 소분류 생성. 2depth 규칙이 강제되는 유일한 지점이다.
     *
     * @param parent 상위 대분류. {@code null}이면 {@link #root(String, int)}를 쓴다
     * @throws BusinessException {@code parent}가 이미 소분류면 CA003(400)
     */
    public static Category child(Category parent, String name, int displayOrder) {
        if (!parent.isRoot()) {
            throw new BusinessException(CategoryErrorCode.CATEGORY_DEPTH_EXCEEDED);
        }
        return new Category(parent, name, displayOrder);
    }

    /**
     * 이름과 전시 순서를 바꾼다. 부모는 바꾸지 않는다.
     *
     * <p>두 필드 모두 필수라 "null이면 변경하지 않음" 규약을 쓰지 않는다.
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
