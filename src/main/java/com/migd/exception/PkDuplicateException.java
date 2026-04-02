package com.migd.exception;

public class PkDuplicateException extends MigrationException {
    public PkDuplicateException(String message, Throwable cause) {
        super(message, cause);
    }
}
