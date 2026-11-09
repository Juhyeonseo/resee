package com.resee.link;

import java.time.LocalDate;

/** POST /links 로 들어오는 입력: URL, 다시 볼 날짜, 메모(선택) */
public record LinkCreateRequest(String url, LocalDate dueDate, String memo) {
}
