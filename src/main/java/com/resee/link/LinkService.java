package com.resee.link;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 링크 기능: 입력 검사, URL 보정, 다시보기 대상 선별 같은 규칙은 모두 여기서 판단한다 */
@Service
public class LinkService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MEMO_MAX = 200;
    private static final int URL_MAX = 2048;

    private final LinkRepository linkRepository;

    public LinkService(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    /** R1, R2: 링크 저장. 검사를 통과하지 못하면 아무것도 저장하지 않는다 */
    @Transactional
    public Link create(LinkCreateRequest request) {
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

        // 핵심 정리(summary)는 2차 반복(작업 5)에서 연결한다. 지금은 비워 두고 저장한다
        return linkRepository.save(new Link(url, null, memo, dueDate));
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
