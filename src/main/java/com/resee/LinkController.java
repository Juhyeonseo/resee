package com.resee;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 화면(index.html)이 부르는 API 전부. 요청을 받아 LinkService 에 넘기고 결과를 JSON 으로 돌려준다.
 * 판단(검사, 규칙)은 하지 않는다.
 */
@RestController
public class LinkController {

    private final LinkService service;
    private final LinkRepository repository;

    public LinkController(LinkService service, LinkRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    // ---------- 요청과 응답의 모양 ----------

    public record PreviewRequest(String url) {}
    public record CreateRequest(String url, LocalDate dueDate, String memo, String summary) {}
    public record StatusRequest(String action, LocalDate dueDate) {}
    public record MemoRequest(String memo) {}

    /** 미리 보기 결과. notice 는 핵심 정리에 실패했을 때만 채워진다 */
    public record PreviewResponse(String url, String summary, String notice) {}

    /** 화면으로 돌려주는 링크 */
    public record LinkResponse(Long id, String url, String summary, String memo, LocalDate dueDate,
                               Link.Status status, LocalDateTime createdAt) {
        static LinkResponse of(Link link) {
            return new LinkResponse(link.getId(), link.getUrl(), link.getSummary(), link.getMemo(),
                    link.getDueDate(), link.getStatus(), link.getCreatedAt());
        }
    }

    // ---------- API ----------

    /** 1단계: 핵심 정리 미리 보기 (R2). 저장하지 않는다 */
    @PostMapping("/links/preview")
    public PreviewResponse preview(@RequestBody PreviewRequest req) {
        LinkService.Preview p = service.preview(req.url());
        String notice = (p.summary() == null) ? "핵심 정리를 하지 못했습니다. 기억할 내용을 메모에 적어 두세요." : null;
        return new PreviewResponse(p.url(), p.summary(), notice);
    }

    /** 2단계: 링크 저장 (R1, R1-2) */
    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkResponse create(@RequestBody CreateRequest req) {
        return LinkResponse.of(service.create(req.url(), req.dueDate(), req.memo(), req.summary()));
    }

    /** 오늘의 다시보기 (R3) */
    @GetMapping("/links/today")
    public List<LinkResponse> today() {
        return service.findToday().stream().map(LinkResponse::of).toList();
    }

    /** 보관함 (R5): /links?status=ALL|UNCHECKED|CHECKED&sort=newest|oldest */
    @GetMapping("/links")
    public List<LinkResponse> all(@RequestParam(defaultValue = "ALL") String status,
                                  @RequestParam(defaultValue = "newest") String sort) {
        return service.findAll(status, sort).stream().map(LinkResponse::of).toList();
    }

    /** 확인 완료 / 다시 보기 (R4) */
    @PatchMapping("/links/{id}/status")
    public LinkResponse changeStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        return LinkResponse.of(service.changeStatus(id, req.action(), req.dueDate()));
    }

    /** 메모 수정 (R1-2) */
    @PatchMapping("/links/{id}/memo")
    public LinkResponse updateMemo(@PathVariable Long id, @RequestBody MemoRequest req) {
        return LinkResponse.of(service.updateMemo(id, req.memo()));
    }

    /** 상태 확인: 앱이 켜져 있고 DB 에 접속되는지 (DB 가 안 되면 오류) */
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "links", repository.count());
    }

    // ---------- 거절 응답: {"error": "안내 문구"} ----------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    /** 날짜를 YYYY-MM-DD 가 아닌 값으로 보낸 경우 등, JSON 을 읽을 수 없을 때 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable() {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "입력 형식이 올바르지 않습니다. 날짜는 YYYY-MM-DD 형식으로 보내 주세요."));
    }
}
