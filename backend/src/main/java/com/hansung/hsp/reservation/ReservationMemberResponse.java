package com.hansung.hsp.reservation;

public record ReservationMemberResponse(String studentId, String name) {
    public static ReservationMemberResponse from(ReservationMember member) {
        return new ReservationMemberResponse(member.getStudentId(), member.getName());
    }
}

