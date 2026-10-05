package com.hansung.hsp.auth;

import com.hansung.hsp.user.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    public record CsrfResponse(String headerName, String token) {}

    @GetMapping("/csrf")
    @Operation(summary = "로그인 전 및 로그인 후 CSRF 토큰 조회")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/login")
    @Operation(summary = "학번과 비밀번호로 로그인", description = "HttpOnly JSESSIONID 쿠키 사용. 성공 후 CSRF 토큰을 다시 조회합니다.")
    public LoginResponse login(@Valid @RequestBody LoginRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        return service.login(input, request, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        service.logout(request, response);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal HspPrincipal principal) {
        return service.me(principal.getUserId());
    }
}

