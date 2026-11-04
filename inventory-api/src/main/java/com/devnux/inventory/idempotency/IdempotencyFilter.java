package com.devnux.inventory.idempotency;

import com.devnux.inventory.web.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Makes POST and PATCH requests safe to retry. A request with an Idempotency-Key runs once; a retry with the
 * same key and body gets the stored response back. See research.md R4.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED = "Idempotent-Replayed";

    private final IdempotencyStore store;

    public IdempotencyFilter(IdempotencyStore store) {
        this.store = store;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String m = request.getMethod();
        return !(m.equals("POST") || m.equals("PATCH"))
                || !request.getRequestURI().startsWith("/api/v1/")
                || request.getHeader(HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key.isBlank() || key.length() > 200) {
            Problems.write(response, HttpStatus.BAD_REQUEST, "invalid-request", "Idempotency-Key must be 1 to 200 characters");
            return;
        }
        String client = SecurityContextHolder.getContext().getAuthentication().getName();
        byte[] body = request.getInputStream().readAllBytes();
        String hash = sha256(request.getMethod() + " " + request.getRequestURI() + "?" + Optional.ofNullable(request.getQueryString()).orElse("") + "\n",
                body);

        if (!store.claim(client, key, hash)) {
            var stored = store.find(client, key);
            if (stored.isEmpty()) { // claimed and released between our two calls: ask the caller to retry
                Problems.write(response, HttpStatus.CONFLICT, "request-in-progress", "A request with this Idempotency-Key is in progress. Retry shortly.");
            } else if (!stored.get().requestHash().equals(hash)) {
                Problems.write(response, HttpStatus.UNPROCESSABLE_CONTENT, "idempotency-key-reused",
                        "This Idempotency-Key was already used for a different request");
            } else if (!"COMPLETED".equals(stored.get().state())) {
                Problems.write(response, HttpStatus.CONFLICT, "request-in-progress", "A request with this Idempotency-Key is in progress. Retry shortly.");
            } else {
                var s = stored.get();
                response.setStatus(s.status());
                if (s.contentType() != null) response.setContentType(s.contentType());
                response.setHeader(REPLAYED, "true");
                response.getOutputStream().write(s.body() == null ? new byte[0] : s.body().getBytes(StandardCharsets.UTF_8));
            }
            return;
        }

        var wrapped = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            chain.doFilter(new CachedBodyRequest(request, body), wrapped);
            int status = wrapped.getStatus();
            if (status >= 200 && status < 300) {
                store.complete(client, key, status, wrapped.getContentType(), new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8));
                stored = true;
            }
        } finally {
            // Failed requests are not remembered, so the caller can fix the problem and retry with the same key.
            if (!stored) store.forget(client, key);
            wrapped.copyBodyToResponse();
        }
    }

    private static String sha256(String prefix, byte[] body) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            d.update(prefix.getBytes(StandardCharsets.UTF_8));
            d.update(body);
            return HexFormat.of().formatHex(d.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lets the controller read a body we already consumed for hashing. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
