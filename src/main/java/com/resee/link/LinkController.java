package com.resee.link;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    /** POST /links : 링크 저장 (R1, R2) */
    @PostMapping
    public ResponseEntity<LinkResponse> create(@RequestBody LinkCreateRequest request) {
        Link saved = linkService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(LinkResponse.from(saved));
    }

    /** GET /links/today : 오늘의 다시보기 (R3) */
    @GetMapping("/today")
    public List<LinkResponse> today() {
        return linkService.findToday().stream().map(LinkResponse::from).toList();
    }
}
