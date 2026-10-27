package com.demo.upimesh.jdbc;

/** Another transaction changed one of these accounts before our update landed. */
public class LedgerConflictException extends RuntimeException {
    public LedgerConflictException(String message) {
        super(message);
    }
}
