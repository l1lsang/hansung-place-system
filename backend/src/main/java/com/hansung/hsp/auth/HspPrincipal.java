package com.hansung.hsp.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class HspPrincipal implements UserDetails, CredentialsContainer, Serializable {
    private static final long serialVersionUID = 1L;
    private final Long userId;
    private final String studentId;
    private final String role;
    private String password;

    public HspPrincipal(Long userId, String studentId, String role, String password) {
        this.userId = userId;
        this.studentId = studentId;
        this.role = role;
        this.password = password;
    }
    public Long getUserId() { return userId; }
    @Override public String getUsername() { return studentId; }
    @Override public String getPassword() { return password; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
    @Override public void eraseCredentials() { password = null; }
}

