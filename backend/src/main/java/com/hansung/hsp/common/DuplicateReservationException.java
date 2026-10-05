package com.hansung.hsp.common;

import org.springframework.http.HttpStatus;

public class DuplicateReservationException extends ApiException {
    public DuplicateReservationException() {
        super(HttpStatus.CONFLICT, "RESERVATION_CONFLICT", "이미 예약된 시간입니다.");
    }
}

