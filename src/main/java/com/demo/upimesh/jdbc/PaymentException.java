package com.demo.upimesh.jdbc;

/** A payment the ledger or the bridge pipeline refuses. */
public class PaymentException extends RuntimeException {

    public PaymentException(String message) {
        super(message);
    }
}
