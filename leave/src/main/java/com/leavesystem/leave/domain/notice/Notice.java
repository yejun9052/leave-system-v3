package com.leavesystem.leave.domain.notice;

import com.leavesystem.leave.common.entity.BaseTimeEntity;
import com.leavesystem.leave.domain.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 공지사항. 설계 문서 9.2.
 *
 * <p>{@link NoticeType#SYSTEM} 공지는 정책 변경 시 {@code published=false} 초안으로 생성되고,
 * 관리자가 확인 후 게시할 때 알림이 만들어진다. 게시된 공지를 수정해도 재발송하지 않는다.
 * 첨부파일은 이번 범위에서 제외한다(14.1).
 */
@Entity
@Getter
@Table(
        name = "notice",
        indexes = @Index(name = "idx_notice__published__pinned__id", columnList = "published, pinned, id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 작성자. 시스템 공지는 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Employee author;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 20)
    private NoticeType type;

    /** 게시 여부. 사원에게는 게시된 공지만 보인다. */
    @Column(name = "published", nullable = false)
    private boolean published;

    /** 목록 상단 고정 여부. */
    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Builder
    private Notice(String title, String content, Employee author, NoticeType type,
                   boolean published, boolean pinned) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.type = type;
        this.published = published;
        this.pinned = pinned;
    }
}
