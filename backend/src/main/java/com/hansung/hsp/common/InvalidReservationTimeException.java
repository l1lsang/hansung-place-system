package com.hansung.hsp.common;

import org.springframework.http.HttpStatus;

public class InvalidReservationTimeException extends ApiException {
    public InvalidReservationTimeException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_RESERVATION_TIME", message);
    }
}

