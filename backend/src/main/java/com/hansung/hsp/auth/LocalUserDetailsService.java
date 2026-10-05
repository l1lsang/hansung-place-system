package com.hansung.hsp.auth;

import com.hansung.hsp.user.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Local credentials are isolated from reservation logic so a future SSO provider
// can authenticate the same HspPrincipal without changing the domain services.
@Service
public class LocalUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    private final UserCredentialRepository credentials;

    public LocalUserDetailsService(UserRepository users, UserCredentialRepository credentials) {
        this.users = users;
        this.credentials = credentials;
    }

    @Override @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String studentId) {
        var user = users.findByStudentId(studentId)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        var credential = credentials.findById(user.getId())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return new HspPrincipal(user.getId(), user.getStudentId(), user.getRole(), credential.getPasswordHash());
    }
}

