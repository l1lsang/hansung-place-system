package com.hansung.hsp.user;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "student_id", nullable = false, length = 20, unique = true)
    private String studentId;
    @Column(nullable = false, length = 100, unique = true)
    private String email;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(nullable = false, length = 20)
    private String role;

    protected User() {}
    public User(String studentId, String email, String name, String role) {
        this.studentId = studentId;
        this.email = email;
        this.name = name;
        this.role = role;
    }
    public Long getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getRole() { return role; }
}

