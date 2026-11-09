package com.resee.link;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 화면으로 돌려주는 링크 정보 */
public record LinkResponse(
        Long id,
        String url,
        String summary,
        String memo,
        LocalDate dueDate,
        LinkStatus status,
        LocalDateTime createdAt
) {
    public static LinkResponse from(Link link) {
        return new LinkResponse(link.getId(), link.getUrl(), link.getSummary(), link.getMemo(),
                link.getDueDate(), link.getStatus(), link.getCreatedAt());
    }
}
