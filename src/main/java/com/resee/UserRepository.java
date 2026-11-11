package com.resee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** users 테이블 읽기·쓰기. findById(아이디), existsById(중복 확인), save 는 JpaRepository 가 기본으로 준다 */
public interface UserRepository extends JpaRepository<User, String> {

    /** 토큰으로 로그인한 사람 찾기 */
    Optional<User> findByToken(String token);
}
