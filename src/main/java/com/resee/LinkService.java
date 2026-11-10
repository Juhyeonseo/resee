package com.resee;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 링크 기능의 규칙을 모두 여기서 판단한다: 입력 검사, URL 보정, 저장, 상태 변경, 조회 조건 */
@Service
public class LinkService {

    private final LinkRepository repository;
    private final SummaryClient summaryClient;

    public LinkService(LinkRepository repository, SummaryClient summaryClient) {
        this.repository = repository;
        this.summaryClient = summaryClient;
    }

    /** 핵심 정리 미리 보기 결과: 보정한 URL 과 3줄 정리(실패하면 null) */
    public record Preview(String url, String summary) {}

    /**
     * 1단계, 핵심 정리 미리 보기 (R2). 아직 저장하지 않는다.
     * URL 이 잘못되면 외부 API 를 부르기 전에 거절한다.
     */
    public Preview preview(String url) {
        url = normalizeUrl(url);
        return new Preview(url, summaryClient.summarize(url));   // 실패하면 summary = null
    }

    /**
     * 2단계, 링크 저장 (R1, R1-2). 1단계에서 받은 핵심 정리를 화면이 함께 보내 준다.
     * 외부 API 를 부르지 않으므로 바로 끝난다. 핵심 정리가 없어도(null) 저장한다.
     */
    @Transactional
    public Link create(String url, LocalDate dueDate, String memo, String summary) {
        url = normalizeUrl(url);
        checkDueDate(dueDate);
        memo = normalizeMemo(memo);
        if (summary != null && summary.isBlank()) {
            summary = null;
        }
        return repository.save(new Link(url, summary, memo, dueDate));
    }

    /** 오늘의 다시보기 (R3): 미확인이고 다시 볼 날짜가 오늘이거나 지난 링크 */
    @Transactional(readOnly = true)
    public List<Link> findToday() {
        return repository.findByStatusAndDueDateLessThanEqualOrderByDueDateAsc(Link.Status.UNCHECKED, today());
    }

    /** 보관함 (R5): status = ALL | UNCHECKED | CHECKED, sort = newest | oldest */
    @Transactional(readOnly = true)
    public List<Link> findAll(String status, String sort) {
        Sort.Direction direction = switch (sort.toLowerCase()) {
            case "newest" -> Sort.Direction.DESC;
            case "oldest" -> Sort.Direction.ASC;
            default -> throw ApiException.badRequest("정렬은 newest 또는 oldest 만 가능합니다.");
        };
        Sort order = Sort.by(direction, "createdAt", "id");

        return switch (status.toUpperCase()) {
            case "ALL" -> repository.findAll(order);
            case "UNCHECKED" -> repository.findByStatus(Link.Status.UNCHECKED, order);
            case "CHECKED" -> repository.findByStatus(Link.Status.CHECKED, order);
            default -> throw ApiException.badRequest("상태는 ALL, UNCHECKED, CHECKED 만 가능합니다.");
        };
    }

    /**
     * 상태 변경 (R4): action = CHECK(확인 완료) 또는 REVIEW(다시 보기 + 새 날짜).
     * 메서드가 끝날 때 @Transactional 이 바뀐 값을 DB 에 반영한다 (save 를 따로 부르지 않아도 됨).
     */
    @Transactional
    public Link changeStatus(Long id, String action, LocalDate dueDate) {
        Link link = repository.findById(id).orElseThrow(() -> ApiException.notFound(id));
        if ("CHECK".equalsIgnoreCase(action)) {
            link.check();
        } else if ("REVIEW".equalsIgnoreCase(action)) {
            checkDueDate(dueDate);
            link.reviewAgain(dueDate);
        } else {
            throw ApiException.badRequest("action 은 CHECK 또는 REVIEW 만 가능합니다.");
        }
        return link;
    }

    /** 메모 직접 입력·수정 (R1-2) */
    @Transactional
    public Link updateMemo(Long id, String memo) {
        Link link = repository.findById(id).orElseThrow(() -> ApiException.notFound(id));
        link.updateMemo(normalizeMemo(memo));
        return link;
    }

    // ---------- 입력 검사 ----------

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    private void checkDueDate(LocalDate dueDate) {
        if (dueDate == null) {
            throw ApiException.badRequest("다시 볼 날짜를 선택해 주세요.");
        }
        if (dueDate.isBefore(today())) {
            throw ApiException.badRequest("다시 볼 날짜는 오늘 이후로 선택해 주세요.");
        }
    }

    /** 빈 메모는 null 로, 200자가 넘으면 거절 */
    private String normalizeMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        memo = memo.trim();
        if (memo.length() > 200) {
            throw ApiException.badRequest("메모는 200자 이내로 입력해 주세요.");
        }
        return memo;
    }

    /** http(s):// 가 없으면 https:// 를 붙이고, 웹 주소 형식(점이 있는 호스트)이 아니면 거절 */
    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            throw ApiException.badRequest("URL을 입력해 주세요.");
        }
        url = url.trim();
        if (!url.toLowerCase().matches("^https?://.*")) {
            url = "https://" + url;
        }
        if (url.length() > 2048) {
            throw ApiException.badRequest("URL이 너무 깁니다.");
        }
        String host;
        try {
            host = URI.create(url).getHost();   // 공백 같은 잘못된 글자가 있으면 예외
        } catch (IllegalArgumentException e) {
            host = null;
        }
        if (host == null || !host.contains(".")) {
            throw ApiException.badRequest("올바른 웹 주소를 입력해 주세요.");
        }
        return url;
    }
}
