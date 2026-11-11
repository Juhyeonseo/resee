package com.resee;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 회원가입과 로그인 API. 둘 다 성공하면 아이디와 토큰을 돌려준다 */
@RestController
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    public record UserRequest(String username, String password) {}
    public record LoginResponse(String username, String token) {}

    /** 회원가입 (가입하면 바로 로그인된 상태) */
    @PostMapping("/users/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse signup(@RequestBody UserRequest req) {
        User user = service.signup(req.username(), req.password());
        return new LoginResponse(user.getUsername(), user.getToken());
    }

    /** 로그인 */
    @PostMapping("/users/login")
    public LoginResponse login(@RequestBody UserRequest req) {
        User user = service.login(req.username(), req.password());
        return new LoginResponse(user.getUsername(), user.getToken());
    }
}
