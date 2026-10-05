package com.hansung.hsp.auth;

import jakarta.persistence.*;

@Entity
@Table(name = "user_credentials")
public class UserCredential {
    @Id @Column(name = "user_id")
    private Long userId;
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    protected UserCredential() {}
    public UserCredential(Long userId, String passwordHash) {
        this.userId = userId;
        this.passwordHash = passwordHash;
    }
    public String getPasswordHash() { return passwordHash; }
}

