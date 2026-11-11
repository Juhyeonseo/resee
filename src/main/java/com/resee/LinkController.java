package com.resee;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 링크 API 전부. 요청을 받아 LinkService 에 넘기고 결과를 JSON 으로 돌려준다. 판단(검사, 규칙)은 하지 않는다.
 * 링크 API 는 로그인이 필요하다: "Authorization: Bearer 토큰" 헤더로 누구인지 확인하고, 그 사람의 링크만 다룬다.
 */
@RestController
public class LinkController {

    private static final String AUTH = "Authorization";

    private final LinkService service;
    private final UserService users;
    private final LinkRepository repository;

    public LinkController(LinkService service, UserService users, LinkRepository repository) {
        this.service = service;
        this.users = users;
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

    // ---------- API (모두 로그인 필요, 헤더가 없거나 틀리면 401) ----------

    /** 1단계: 핵심 정리 미리 보기 (R2). 저장하지 않는다 */
    @PostMapping("/links/preview")
    public PreviewResponse preview(@RequestHeader(value = AUTH, required = false) String auth,
                                   @RequestBody PreviewRequest req) {
        users.userOf(auth);   // 로그인한 사람만 (내 Gemini 키를 아무나 쓰지 못하게)
        LinkService.Preview p = service.preview(req.url());
        String notice = (p.summary() == null) ? "핵심 정리를 하지 못했습니다. 기억할 내용을 메모에 적어 두세요." : null;
        return new PreviewResponse(p.url(), p.summary(), notice);
    }

    /** 2단계: 링크 저장 (R1, R1-2) */
    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkResponse create(@RequestHeader(value = AUTH, required = false) String auth,
                               @RequestBody CreateRequest req) {
        String user = users.userOf(auth);
        return LinkResponse.of(service.create(user, req.url(), req.dueDate(), req.memo(), req.summary()));
    }

    /** 오늘의 다시보기 (R3) */
    @GetMapping("/links/today")
    public List<LinkResponse> today(@RequestHeader(value = AUTH, required = false) String auth) {
        String user = users.userOf(auth);
        return service.findToday(user).stream().map(LinkResponse::of).toList();
    }

    /** 보관함 (R5): /links?status=ALL|UNCHECKED|CHECKED&sort=newest|oldest */
    @GetMapping("/links")
    public List<LinkResponse> all(@RequestHeader(value = AUTH, required = false) String auth,
                                  @RequestParam(defaultValue = "ALL") String status,
                                  @RequestParam(defaultValue = "newest") String sort) {
        String user = users.userOf(auth);
        return service.findAll(user, status, sort).stream().map(LinkResponse::of).toList();
    }

    /** 확인 완료 / 다시 보기 (R4) */
    @PatchMapping("/links/{id}/status")
    public LinkResponse changeStatus(@RequestHeader(value = AUTH, required = false) String auth,
                                     @PathVariable Long id, @RequestBody StatusRequest req) {
        String user = users.userOf(auth);
        return LinkResponse.of(service.changeStatus(user, id, req.action(), req.dueDate()));
    }

    /** 메모 수정 (R1-2) */
    @PatchMapping("/links/{id}/memo")
    public LinkResponse updateMemo(@RequestHeader(value = AUTH, required = false) String auth,
                                   @PathVariable Long id, @RequestBody MemoRequest req) {
        String user = users.userOf(auth);
        return LinkResponse.of(service.updateMemo(user, id, req.memo()));
    }

    /** 링크 삭제. 성공하면 204 (돌려줄 내용 없음) */
    @DeleteMapping("/links/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable Long id) {
        service.delete(users.userOf(auth), id);
    }

    /** 상태 확인: 앱이 켜져 있고 DB 에 접속되는지 (로그인 필요 없음) */
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "links", repository.count());
    }
}
