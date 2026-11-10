package com.resee;

import org.springframework.http.HttpStatus;

/** 요청을 거절할 때 쓰는 예외. LinkController 가 {"error": message} 응답으로 바꾼다 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** 400: 입력이 잘못됨 */
    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    /** 404: 저장되어 있지 않은 링크 */
    public static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "저장되어 있지 않은 링크입니다. (id=" + id + ")");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
