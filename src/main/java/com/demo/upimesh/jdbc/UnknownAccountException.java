package com.demo.upimesh.jdbc;

/** The sender or the receiver is not one of the demo accounts. */
public class UnknownAccountException extends PaymentException {

    public UnknownAccountException(String message) {
        super(message);
    }
}
