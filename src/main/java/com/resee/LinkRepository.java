package com.resee;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * links 테이블 읽기·쓰기.
 * save, findById, findAll, count 는 JpaRepository 가 기본으로 주고,
 * 아래 두 메서드는 이름만 보고 Spring Data JPA 가 SQL 을 만들어 준다.
 */
public interface LinkRepository extends JpaRepository<Link, Long> {

    /** 오늘의 다시보기: 상태가 status 이고 다시 볼 날짜가 date 이하, 날짜 빠른 순 */
    List<Link> findByStatusAndDueDateLessThanEqualOrderByDueDateAsc(Link.Status status, LocalDate date);

    /** 보관함: 상태로 거르고 sort 순서로 */
    List<Link> findByStatus(Link.Status status, Sort sort);
}
