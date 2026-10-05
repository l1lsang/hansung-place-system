package com.hansung.hsp.auth;

import com.hansung.hsp.common.ApiException;
import com.hansung.hsp.user.UserRepository;
import com.hansung.hsp.user.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.*;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    private final CsrfTokenRepository csrfRepository;
    private final UserRepository users;

    public AuthService(AuthenticationManager authenticationManager, SecurityContextRepository contexts,
            SessionAuthenticationStrategy sessions, CsrfTokenRepository csrfRepository, UserRepository users) {
        this.authenticationManager = authenticationManager;
        this.contexts = contexts;
        this.sessions = sessions;
        this.csrfRepository = csrfRepository;
        this.users = users;
    }

    public LoginResponse login(LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("INVALID_PASSWORD_LENGTH", "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(input.studentId(), input.password()));
        // Manual JSON login must rotate the session ID and CSRF token, then explicitly persist the context.
        sessions.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return new LoginResponse(me(((HspPrincipal) authentication.getPrincipal()).getUserId()));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return UserResponse.from(users.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid user")));
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CsrfLogoutHandler(csrfRepository).logout(request, response, authentication);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
    }
}

