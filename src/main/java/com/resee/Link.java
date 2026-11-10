package com.resee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** links 테이블의 한 행 = 저장한 링크 하나. 필드 이름이 그대로 열 이름이 된다 (dueDate → due_date) */
@Entity
@Table(name = "links")
public class Link {

    /** 링크 상태는 두 가지뿐: 미확인, 확인 완료 */
    public enum Status { UNCHECKED, CHECKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String summary;              // AI 핵심 정리. 실패하면 null

    @Column(length = 200)
    private String memo;                 // 저장 이유 (선택)

    @Column(nullable = false)
    private LocalDate dueDate;           // 다시 볼 날짜

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.UNCHECKED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;     // 저장 시각, 보관함 정렬 기준

    protected Link() {
        // JPA가 DB에서 읽어 올 때 쓰는 빈 생성자
    }

    public Link(String url, String summary, String memo, LocalDate dueDate) {
        this.url = url;
        this.summary = summary;
        this.memo = memo;
        this.dueDate = dueDate;
        this.createdAt = LocalDateTime.now();
    }

    /** '확인 완료' 버튼 */
    public void check() {
        this.status = Status.CHECKED;
    }

    /** '다시 보기' 버튼: 미확인으로 두고 날짜만 바꾼다 */
    public void reviewAgain(LocalDate newDueDate) {
        this.status = Status.UNCHECKED;
        this.dueDate = newDueDate;
    }

    public void updateMemo(String memo) {
        this.memo = memo;
    }

    public Long getId() { return id; }
    public String getUrl() { return url; }
    public String getSummary() { return summary; }
    public String getMemo() { return memo; }
    public LocalDate getDueDate() { return dueDate; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
