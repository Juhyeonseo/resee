package com.resee;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** 모든 컨트롤러의 거절을 {"error": "안내 문구"} 응답으로 바꾼다 */
@RestControllerAdvice
public class ApiExceptionHandler {

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
