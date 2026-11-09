package com.resee.link;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 화면으로 돌려주는 링크 정보. notice 는 저장 직후에만 채워지는 안내 문구 */
public record LinkResponse(
        Long id,
        String url,
        String summary,
        String memo,
        LocalDate dueDate,
        LinkStatus status,
        LocalDateTime createdAt,
        String notice
) {
    public static final String SUMMARY_FAILED_NOTICE = "핵심 정리에 실패했습니다. 링크는 저장되었습니다";

    public static LinkResponse from(Link link) {
        return from(link, null);
    }

    public static LinkResponse from(Link link, String notice) {
        return new LinkResponse(link.getId(), link.getUrl(), link.getSummary(), link.getMemo(),
                link.getDueDate(), link.getStatus(), link.getCreatedAt(), notice);
    }
}
