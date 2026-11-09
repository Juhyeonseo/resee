package com.resee.link;

/** 입력 검사에서 거절할 때 쓰는 예외. 화면에는 400 오류와 message 가 전달된다 */
public class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) {
        super(message);
    }
}
