package com.resee.link;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 요청 처리: 요청을 받아 LinkService 에 넘기고 결과를 돌려준다. 판단은 하지 않는다 */
@RestController
@RequestMapping("/links")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    /** POST /links : 링크 저장 (R1, R1-2, R2) */
    @PostMapping
    public ResponseEntity<LinkResponse> create(@RequestBody LinkCreateRequest request) {
        LinkSaveResult result = linkService.create(request);
        String notice = result.summaryFailed() ? LinkResponse.SUMMARY_FAILED_NOTICE : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(LinkResponse.from(result.link(), notice));
    }

    /** GET /links/today : 오늘의 다시보기 (R3) */
    @GetMapping("/today")
    public List<LinkResponse> today() {
        return linkService.findToday().stream().map(LinkResponse::from).toList();
    }

    /** GET /links?status=ALL|UNCHECKED|CHECKED&sort=newest|oldest : 보관함 (R5) */
    @GetMapping
    public List<LinkResponse> all(@RequestParam(defaultValue = "ALL") String status,
                                  @RequestParam(defaultValue = "newest") String sort) {
        return linkService.findAll(status, sort).stream().map(LinkResponse::from).toList();
    }

    /** PATCH /links/{id}/status : 확인 완료 / 다시 보기 (R4) */
    @PatchMapping("/{id}/status")
    public LinkResponse changeStatus(@PathVariable Long id, @RequestBody LinkStatusRequest request) {
        return LinkResponse.from(linkService.changeStatus(id, request));
    }

    /** PATCH /links/{id}/memo : 메모 수정 (R1-2) */
    @PatchMapping("/{id}/memo")
    public LinkResponse updateMemo(@PathVariable Long id, @RequestBody LinkMemoRequest request) {
        return LinkResponse.from(linkService.updateMemo(id, request));
    }
}
