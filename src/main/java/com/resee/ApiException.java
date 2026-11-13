package com.resee;

import org.springframework.http.HttpStatus;


public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }


    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }


    public static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "저장되어 있지 않은 링크입니다. (id=" + id + ")");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
