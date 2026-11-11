package com.resee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** users 테이블의 한 행 = 가입한 사람 한 명 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(length = 20)
    private String username;             // 아이디. 기본 키라서 중복될 수 없다

    @Column(nullable = false)
    private String passwordHash;         // 비밀번호를 BCrypt 로 바꾼 값. 원래 비밀번호는 저장하지 않는다

    @Column(length = 36)
    private String token;                // 로그인할 때 새로 만드는 무작위 문자열. 화면이 요청마다 보낸다

    protected User() {
        // JPA가 DB에서 읽어 올 때 쓰는 빈 생성자
    }

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getToken() { return token; }
}
