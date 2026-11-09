package com.resee;

import com.resee.link.LinkRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 1단계 확인용: 앱이 응답하고 DB에 접속되는지 확인한다 */
@RestController
public class HealthController {

    private final LinkRepository linkRepository;

    public HealthController(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        // DB에 접속되지 않으면 여기서 오류가 난다
        long count = linkRepository.count();
        return Map.of("status", "ok", "links", count);
    }
}
