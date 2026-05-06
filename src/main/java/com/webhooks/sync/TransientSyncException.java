package com.webhooks.sync;

/** Thrown when the sync can succeed after parent/dependency data appears (retriable). */
public class TransientSyncException extends RuntimeException {

    public TransientSyncException(String message) {
        super(message);
    }
}
