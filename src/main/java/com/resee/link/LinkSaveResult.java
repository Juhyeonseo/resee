package com.resee.link;

/** 링크 저장 결과: 저장된 링크와 핵심 정리 실패 여부 */
public record LinkSaveResult(Link link, boolean summaryFailed) {
}
