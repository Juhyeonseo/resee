package com.resee.link;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** links 테이블 한 행 = 저장한 링크 하나 (개발 문서 5-2 links 테이블) */
@Entity
@Table(name = "links")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String url;

    /** AI 핵심 정리. 실패하면 비어 있다(R1-2) */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** 저장 이유 메모. 선택 입력, 200자 이내 */
    @Column(length = 200)
    private String memo;

    /** 다시 볼 날짜 */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LinkStatus status = LinkStatus.UNCHECKED;

    /** 저장 시각. 보관함 정렬 기준(R5) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Link() {
        // JPA가 사용하는 기본 생성자
    }

    public Link(String url, String summary, String memo, LocalDate dueDate) {
        this.url = url;
        this.summary = summary;
        this.memo = memo;
        this.dueDate = dueDate;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /** R4 '확인 완료' */
    public void check() {
        this.status = LinkStatus.CHECKED;
    }

    /** R4 '다시 보기': 미확인으로 두고 다시 볼 날짜만 바꾼다 */
    public void reviewAgain(LocalDate newDueDate) {
        this.status = LinkStatus.UNCHECKED;
        this.dueDate = newDueDate;
    }

    /** R1-2 메모 직접 입력·수정 */
    public void updateMemo(String memo) {
        this.memo = memo;
    }

    public Long getId() { return id; }
    public String getUrl() { return url; }
    public String getSummary() { return summary; }
    public String getMemo() { return memo; }
    public LocalDate getDueDate() { return dueDate; }
    public LinkStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
