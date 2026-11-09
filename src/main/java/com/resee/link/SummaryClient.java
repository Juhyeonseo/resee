package com.resee.link;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 요약 연동: Jina Reader로 본문을 가져오고, Gemini로 3줄 핵심 정리를 받는다.
 * 어떤 이유로든 정리를 못 하면 Optional.empty()를 돌려준다(R1-2). 링크 저장 여부는 LinkService가 판단한다.
 */
@Component
public class SummaryClient {

    private static final Logger log = LoggerFactory.getLogger(SummaryClient.class);

    /** 제한 시간: 0단계 실측(본문 추출 10~11초, 핵심 정리 약 1.5초)의 여유를 둔 값. 개발 문서 6-1 */
    private static final Duration EXTRACT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration SUMMARY_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BODY_CHARS = 8000;

    static final String NO_CONTENT = "정리할 내용 없음";
    private static final String PROMPT =
            "다음 웹페이지 본문의 핵심을 한국어로 3줄 이내로 정리해 줘. "
            + "마감일, 기간, 비용 같은 날짜나 숫자 정보가 있으면 꼭 포함해 줘. "
            + "본문에 정리할 핵심 정보가 없으면 다른 말 없이 '" + NO_CONTENT + "'이라고만 답해 줘.";

    private final RestClient jina;
    private final RestClient gemini;
    private final String apiKey;
    private final String model;

    public SummaryClient(@Value("${gemini.api-key:}") String apiKey,
                         @Value("${gemini.model:gemini-3.5-flash-lite}") String model) {
        this.jina = RestClient.builder().requestFactory(factory(EXTRACT_TIMEOUT)).build();
        this.gemini = RestClient.builder()
                .requestFactory(factory(SUMMARY_TIMEOUT))
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    private static JdkClientHttpRequestFactory factory(Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    public Optional<String> summarize(String url) {
        // ① Jina Reader: URL 앞에 r.jina.ai 를 붙이면 본문을 텍스트로 돌려준다
        String body;
        try {
            byte[] bytes = jina.get()
                    .uri(URI.create("https://r.jina.ai/" + url))
                    .retrieve()
                    .body(byte[].class);
            body = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("본문 추출 실패: {} ({})", url, e.getMessage());
            return Optional.empty();
        }
        if (body.isBlank()) {
            log.warn("본문이 비어 있음: {}", url);
            return Optional.empty();
        }
        if (body.length() > MAX_BODY_CHARS) {
            body = body.substring(0, MAX_BODY_CHARS);
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY 가 설정되지 않아 핵심 정리를 건너뜀");
            return Optional.empty();
        }

        // ② Gemini: 본문을 보내 3줄 정리를 받는다
        try {
            GeminiResponse response = gemini.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GeminiRequest.of(PROMPT + "\n\n" + body))
                    .retrieve()
                    .body(GeminiResponse.class);
            String text = response == null ? null : response.firstText();
            if (text == null || text.isBlank() || text.contains(NO_CONTENT)) {
                log.warn("정리할 내용 없음: {}", url);
                return Optional.empty();
            }
            return Optional.of(text.trim());
        } catch (Exception e) {
            log.warn("핵심 정리 실패: {} ({})", url, e.getMessage());
            return Optional.empty();
        }
    }

    // ----- Gemini 요청·응답 모양 (필요한 칸만) -----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiRequest(List<Content> contents) {
        static GeminiRequest of(String text) {
            return new GeminiRequest(List.of(new Content(List.of(new Part(text)))));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates) {
        String firstText() {
            if (candidates == null || candidates.isEmpty()) return null;
            Content content = candidates.get(0).content();
            if (content == null || content.parts() == null || content.parts().isEmpty()) return null;
            return content.parts().get(0).text();
        }
    }
}
