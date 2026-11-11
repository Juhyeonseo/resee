package com.resee;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * links 테이블 읽기·쓰기.
 * save, count 는 JpaRepository 가 기본으로 주고,
 * 아래 메서드는 이름만 보고 Spring Data JPA 가 SQL 을 만들어 준다. 모든 조회에 owner(아이디) 조건이 붙는다.
 */
public interface LinkRepository extends JpaRepository<Link, Long> {

    /** 오늘의 다시보기: 이 아이디의 링크 중 상태가 status 이고 다시 볼 날짜가 date 이하, 날짜 빠른 순 */
    List<Link> findByOwnerAndStatusAndDueDateLessThanEqualOrderByDueDateAsc(String owner, Link.Status status, LocalDate date);

    /** 보관함: 이 아이디의 링크 전체 / 상태로 거른 것 */
    List<Link> findByOwner(String owner, Sort sort);
    List<Link> findByOwnerAndStatus(String owner, Link.Status status, Sort sort);

    /** 이 아이디의 링크 하나 (다른 사람 링크면 비어 있음) */
    Optional<Link> findByIdAndOwner(Long id, String owner);
}
