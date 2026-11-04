package com.devnux.inventory.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 problem details, including from filters that run before Spring MVC. */
public final class Problems {
    private Problems() {}

    public static ProblemDetail of(HttpStatus status, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setProperty("code", code);
        return p;
    }

    /** Writes a problem directly to the servlet response, without needing a JSON library. */
    public static void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detail.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"code\":\"" + code + "\"}");
    }
}
