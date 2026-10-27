package com.demo.upimesh.service;

import com.demo.upimesh.jdbc.PaymentException;

/**
 * The signed time is too old, or it is too far in the future.
 * The claim stays, so the same bytes are not settled later.
 */
public class StalePacketException extends PaymentException {

    public StalePacketException(String reason) {
        super(reason);
    }
}
