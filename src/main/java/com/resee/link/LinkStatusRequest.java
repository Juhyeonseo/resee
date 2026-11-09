package com.resee.link;

import java.time.LocalDate;

/** PATCH /links/{id}/status 입력. action 은 CHECK(확인 완료) 또는 REVIEW(다시 보기), REVIEW 일 때 dueDate 필수 */
public record LinkStatusRequest(String action, LocalDate dueDate) {
}
