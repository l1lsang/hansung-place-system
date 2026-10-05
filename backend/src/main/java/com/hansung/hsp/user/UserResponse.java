package com.hansung.hsp.user;

public record UserResponse(Long id, String studentId, String email, String name, String role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getStudentId(), user.getEmail(), user.getName(), user.getRole());
    }
}

