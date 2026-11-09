package com.resee;

import com.resee.link.InvalidInputException;
import com.resee.link.LinkNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** 거절된 요청을 {"error": "안내 문구"} 형태의 응답으로 바꾼다 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInput(InvalidInputException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(LinkNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(LinkNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    /** 날짜를 YYYY-MM-DD 가 아닌 값으로 보낸 경우 등 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "입력 형식이 올바르지 않습니다. 날짜는 YYYY-MM-DD 형식으로 보내 주세요."));
    }
}
