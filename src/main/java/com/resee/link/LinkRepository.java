package com.resee.link;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/** 데이터 접근: links 테이블 읽기·쓰기. 기본 저장·조회 메서드는 Spring Data JPA가 만들어 준다 */
public interface LinkRepository extends JpaRepository<Link, Long> {

    /**
     * 오늘의 다시보기 대상(R3): 상태가 status 이고 다시 볼 날짜가 date 이하인 링크를 날짜가 빠른 순으로.
     * 메서드 이름만으로 Spring Data JPA가 조회 SQL을 만들어 준다.
     */
    List<Link> findByStatusAndDueDateLessThanEqualOrderByDueDateAsc(LinkStatus status, LocalDate date);
}
