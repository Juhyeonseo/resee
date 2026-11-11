package com.resee;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 회원가입, 로그인, "이 요청은 누구인가" 확인.
 * 로그인하면 무작위 토큰을 만들어 저장하고 화면에 돌려준다. 화면은 이후 요청마다
 * "Authorization: Bearer 토큰" 헤더를 붙이고, 서버는 그 토큰으로 사람을 찾는다.
 */
@Service
public class UserService {

    private final UserRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    /** 회원가입: 아이디 2~20자, 비밀번호 4자 이상, 이미 있는 아이디면 거절. 가입하면 바로 로그인한다 */
    @Transactional
    public User signup(String username, String password) {
        username = (username == null) ? "" : username.trim();
        if (username.length() < 2 || username.length() > 20) {
            throw ApiException.badRequest("아이디는 2~20자로 입력해 주세요.");
        }
        if (password == null || password.length() < 4) {
            throw ApiException.badRequest("비밀번호는 4자 이상으로 입력해 주세요.");
        }
        if (repository.existsById(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 있는 아이디입니다.");
        }
        User user = new User(username, encoder.encode(password));
        user.setToken(UUID.randomUUID().toString());
        return repository.save(user);
    }

    /** 로그인: 아이디와 비밀번호가 맞으면 새 토큰을 만든다 (예전 토큰은 더 이상 쓸 수 없음) */
    @Transactional
    public User login(String username, String password) {
        User user = repository.findById(username == null ? "" : username.trim())
                .filter(u -> password != null && encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 맞지 않습니다."));
        user.setToken(UUID.randomUUID().toString());
        return user;
    }

    /** "Bearer 토큰" 헤더로 로그인한 사람의 아이디를 찾는다. 없거나 틀리면 401 */
    @Transactional(readOnly = true)
    public String userOf(String authorization) {
        String token = (authorization == null) ? "" : authorization.replace("Bearer ", "").trim();
        return repository.findByToken(token)
                .map(User::getUsername)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }
}
