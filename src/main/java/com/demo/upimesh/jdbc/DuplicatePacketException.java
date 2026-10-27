package com.demo.upimesh.jdbc;

/** The ledger already has a row for this ciphertext hash. */
public class DuplicatePacketException extends RuntimeException {
    public DuplicatePacketException(String packetHash) {
        super(packetHash);
    }
}
