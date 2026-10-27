package com.demo.upimesh.io;

/** The packet file could not be written or read. Callers must catch this. */
public class MeshException extends Exception {

    public MeshException(String message, Throwable cause) {
        super(message, cause);
    }
}
