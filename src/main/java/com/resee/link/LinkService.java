package com.resee.link;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** 링크 기능: 입력 검사, URL 보정, 저장 내용 결정, 다시보기 대상 선별 같은 규칙은 모두 여기서 판단한다 */
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
     * 검사를 통과하지 못하면 외부 API도 DB도 부르지 않는다.
     * 핵심 정리에 실패해도 링크는 저장한다.
     * (외부 API를 기다리는 동안 DB 연결을 붙잡지 않도록 이 메서드에는 @Transactional 을 붙이지 않는다)
     */
    public LinkSaveResult create(LinkCreateRequest request) {
        String url = normalizeUrl(request.url());

        LocalDate dueDate = request.dueDate();
        if (dueDate == null) {
            throw new InvalidInputException("다시 볼 날짜를 선택해 주세요.");
        }
        if (dueDate.isBefore(today())) {
            throw new InvalidInputException("다시 볼 날짜는 오늘 이후로 선택해 주세요.");
        }

        String memo = request.memo();
        if (memo == null || memo.isBlank()) {
            memo = null;
        } else {
            memo = memo.trim();
            if (memo.length() > MEMO_MAX) {
                throw new InvalidInputException("메모는 200자 이내로 입력해 주세요.");
            }
        }

        Optional<String> summary = summaryClient.summarize(url);
        Link saved = linkRepository.save(new Link(url, summary.orElse(null), memo, dueDate));
        return new LinkSaveResult(saved, summary.isEmpty());
    }

    /** R3: 오늘의 다시보기. 미확인이고 다시 볼 날짜가 오늘이거나 지난 링크. 읽기만 한다 */
    @Transactional(readOnly = true)
    public List<Link> findToday() {
        return linkRepository.findByStatusAndDueDateLessThanEqualOrderByDueDateAsc(LinkStatus.UNCHECKED, today());
    }

    private LocalDate today() {
        return LocalDate.now(KST);
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
