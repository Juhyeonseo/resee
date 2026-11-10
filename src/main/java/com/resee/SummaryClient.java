package com.resee;

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

/**
 * 핵심 정리: ① Jina Reader 로 웹페이지 본문을 텍스트로 받고, ② Gemini 에 보내 3줄 정리를 받는다.
 * 어느 단계든 실패하면 null 을 돌려준다. 링크 저장 여부는 LinkService 가 정한다.
 */
@Component
public class SummaryClient {

    private static final Logger log = LoggerFactory.getLogger(SummaryClient.class);

    private static final String NO_CONTENT = "정리할 내용 없음";
    private static final String PROMPT =
            "다음 웹페이지 본문의 핵심을 한국어로 3줄 이내로 정리해 줘. "
            + "마감일, 기간, 비용 같은 날짜나 숫자 정보가 있으면 꼭 포함해 줘. "
            + "본문에 정리할 핵심 정보가 없으면 다른 말 없이 '" + NO_CONTENT + "'이라고만 답해 줘.";

    private final RestClient jina = client(Duration.ofSeconds(20));    // 본문 추출: 실측 10~11초
    private final RestClient gemini = client(Duration.ofSeconds(10));  // 핵심 정리: 실측 약 1.5초
    private final String apiKey;
    private final String model;

    public SummaryClient(@Value("${gemini.api-key}") String apiKey, @Value("${gemini.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    /** 3줄 핵심 정리를 돌려준다. 실패하면 null */
    public String summarize(String url) {
        // ① Jina Reader: 주소 앞에 https://r.jina.ai/ 를 붙이면 본문을 텍스트로 돌려준다
        String body;
        try {
            byte[] bytes = jina.get().uri(URI.create("https://r.jina.ai/" + url)).retrieve().body(byte[].class);
            body = (bytes == null) ? "" : new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("본문 추출 실패: {} ({})", url, e.getMessage());
            return null;
        }
        if (body.isBlank()) {
            log.warn("본문이 비어 있음: {}", url);
            return null;
        }
        if (body.length() > 8000) {
            body = body.substring(0, 8000);   // 너무 긴 본문은 앞부분만 보낸다
        }
        if (apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY 가 없어 핵심 정리를 건너뜀");
            return null;
        }

        // ② Gemini: 프롬프트 + 본문을 보내고, 응답의 candidates[0].content.parts[0].text 를 꺼낸다
        try {
            GeminiResponse response = gemini.post()
                    .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GeminiRequest.of(PROMPT + "\n\n" + body))
                    .retrieve()
                    .body(GeminiResponse.class);
            String text = (response == null) ? null : response.firstText();
            if (text == null || text.isBlank() || text.contains(NO_CONTENT)) {
                log.warn("정리할 내용 없음: {}", url);
                return null;
            }
            return text.trim();
        } catch (Exception e) {
            log.warn("핵심 정리 실패: {} ({})", url, e.getMessage());
            return null;
        }
    }

    /** Gemini 에 보내는 모양: {"contents":[{"parts":[{"text": "..."}]}]} */
    record GeminiRequest(List<Content> contents) {
        static GeminiRequest of(String text) {
            return new GeminiRequest(List.of(new Content(List.of(new Part(text)))));
        }
    }

    /** Gemini 응답에서 필요한 칸만 받는 모양. 나머지 칸은 무시한다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates) {
        String firstText() {
            try {
                return candidates.get(0).content().parts().get(0).text();
            } catch (RuntimeException e) {   // 칸이 비어 있으면 null
                return null;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {}

    /** 제한 시간(readTimeout)을 둔 HTTP 클라이언트. 리다이렉트는 따라간다 */
    private static RestClient client(Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }
}
