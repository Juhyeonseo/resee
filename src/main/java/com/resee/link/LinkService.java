package com.resee.link;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** 링크 기능: 입력 검사, URL 보정, 저장 내용 결정, 상태 변경, 조회 조건 같은 규칙은 모두 여기서 판단한다 */
@Service
public class LinkService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MEMO_MAX = 200;
    private static final int URL_MAX = 2048;

    private final LinkRepository linkRepository;
    private final SummaryClient summaryClient;

    public LinkService(LinkRepository linkRepository, SummaryClient summaryClient) {
        this.linkRepository = linkRepository;
        this.summaryClient = summaryClient;
    }

    /**
     * R1, R2, R1-2: 링크 저장.
     * 검사를 통과하지 못하면 외부 API도 DB도 부르지 않는다. 핵심 정리에 실패해도 링크는 저장한다.
     * (외부 API를 기다리는 동안 DB 연결을 붙잡지 않도록 이 메서드에는 @Transactional 을 붙이지 않는다)
     */
    public LinkSaveResult create(LinkCreateRequest request) {
        String url = normalizeUrl(request.url());
        LocalDate dueDate = checkDueDate(request.dueDate());
        String memo = normalizeMemo(request.memo());

        Optional<String> summary = summaryClient.summarize(url);
        Link saved = linkRepository.save(new Link(url, summary.orElse(null), memo, dueDate));
        return new LinkSaveResult(saved, summary.isEmpty());
    }

    /** R3: 오늘의 다시보기. 미확인이고 다시 볼 날짜가 오늘이거나 지난 링크. 읽기만 한다 */
    @Transactional(readOnly = true)
    public List<Link> findToday() {
        return linkRepository.findByStatusAndDueDateLessThanEqualOrderByDueDateAsc(LinkStatus.UNCHECKED, today());
    }

    /** R5: 보관함. status = ALL/UNCHECKED/CHECKED, sort = newest/oldest (저장 시각 기준) */
    @Transactional(readOnly = true)
    public List<Link> findAll(String status, String sort) {
        Sort.Direction direction;
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("newest")) {
            direction = Sort.Direction.DESC;
        } else if (sort.equalsIgnoreCase("oldest")) {
            direction = Sort.Direction.ASC;
        } else {
            throw new InvalidInputException("정렬은 newest 또는 oldest 만 가능합니다.");
        }
        Sort order = Sort.by(direction, "createdAt").and(Sort.by(direction, "id"));

        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            return linkRepository.findAll(order);
        }
        try {
            return linkRepository.findByStatus(LinkStatus.valueOf(status.toUpperCase()), order);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("상태는 ALL, UNCHECKED, CHECKED 만 가능합니다.");
        }
    }

    /** R4: '확인 완료'(CHECK) 또는 '다시 보기'(REVIEW + 새 날짜). 버튼을 눌렀을 때만 바뀐다 */
    @Transactional
    public Link changeStatus(Long id, LinkStatusRequest request) {
        Link link = linkRepository.findById(id).orElseThrow(() -> new LinkNotFoundException(id));
        String action = request.action() == null ? "" : request.action().toUpperCase();
        switch (action) {
            case "CHECK" -> link.check();
            case "REVIEW" -> link.reviewAgain(checkDueDate(request.dueDate()));
            default -> throw new InvalidInputException("action 은 CHECK 또는 REVIEW 만 가능합니다.");
        }
        return link; // @Transactional 이 끝날 때 바뀐 값이 DB에 반영된다
    }

    /** R1-2: 메모 직접 입력·수정 */
    @Transactional
    public Link updateMemo(Long id, LinkMemoRequest request) {
        Link link = linkRepository.findById(id).orElseThrow(() -> new LinkNotFoundException(id));
        link.updateMemo(normalizeMemo(request.memo()));
        return link;
    }

    private LocalDate today() {
        return LocalDate.now(KST);
    }

    private LocalDate checkDueDate(LocalDate dueDate) {
        if (dueDate == null) {
            throw new InvalidInputException("다시 볼 날짜를 선택해 주세요.");
        }
        if (dueDate.isBefore(today())) {
            throw new InvalidInputException("다시 볼 날짜는 오늘 이후로 선택해 주세요.");
        }
        return dueDate;
    }

    private String normalizeMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        String trimmed = memo.trim();
        if (trimmed.length() > MEMO_MAX) {
            throw new InvalidInputException("메모는 200자 이내로 입력해 주세요.");
        }
        return trimmed;
    }

    /** URL이 비었거나 웹 주소 형식이 아니면 거절, http(s):// 가 없으면 https:// 를 붙인다 */
    static String normalizeUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("URL을 입력해 주세요.");
        }
        String url = raw.trim();
        if (url.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidInputException("올바른 웹 주소를 입력해 주세요.");
        }
        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            url = "https://" + url;
        }
        if (url.length() > URL_MAX) {
            throw new InvalidInputException("URL이 너무 깁니다.");
        }
        String host;
        try {
            host = new URI(url).getHost();
        } catch (URISyntaxException e) {
            throw new InvalidInputException("올바른 웹 주소를 입력해 주세요.");
        }
        if (host == null || !host.contains(".")) {
            throw new InvalidInputException("올바른 웹 주소를 입력해 주세요.");
        }
        return url;
    }
}
