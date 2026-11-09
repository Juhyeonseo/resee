package com.resee.link;

/** 저장되어 있지 않은 링크에 대한 요청. 화면에는 404 오류가 전달된다 */
public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(Long id) {
        super("저장되어 있지 않은 링크입니다. (id=" + id + ")");
    }
}
