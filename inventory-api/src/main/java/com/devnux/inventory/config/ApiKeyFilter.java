package com.devnux.inventory.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates callers by the X-API-Key header. Unknown keys stay anonymous and are rejected by the security rules. */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    private final List<ApiKey> keys;

    public ApiKeyFilter(List<ApiKey> keys) {
        this.keys = keys;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null) {
            byte[] given = presented.getBytes(StandardCharsets.UTF_8);
            for (ApiKey key : keys) {
                // Constant-time comparison so response timing doesn't leak key prefixes.
                if (MessageDigest.isEqual(given, key.key().getBytes(StandardCharsets.UTF_8))) {
                    var auth = new UsernamePasswordAuthenticationToken(key.name(), null,
                            key.authorities().stream().map(SimpleGrantedAuthority::new).toList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    break;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
