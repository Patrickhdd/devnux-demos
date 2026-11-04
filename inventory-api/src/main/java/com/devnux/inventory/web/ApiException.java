package com.devnux.inventory.web;

import org.springframework.http.HttpStatus;

/** A failure the caller can act on. {@code code} is stable and documented in the OpenAPI contract. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static ApiException notFound(String what, String code) {
        return new ApiException(HttpStatus.NOT_FOUND, "not-found", what + " " + code + " does not exist");
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid-request", detail);
    }

    public static ApiException insufficientStock(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "insufficient-stock", detail);
    }

    public static ApiException holdNotActive(String status) {
        return new ApiException(HttpStatus.CONFLICT, "hold-not-active", "This hold is " + status.toLowerCase() + ", not active");
    }
}
