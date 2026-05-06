package com.webhooks.woocommerce;

/**
 * Thrown when Site B rejects a WooCommerce REST call (4xx/5xx or parse error).
 */
public class WooCommerceSyncException extends RuntimeException {

    private final int statusCode;

    public WooCommerceSyncException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public WooCommerceSyncException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
