package com.devnux.inventory.config;

import com.devnux.inventory.idempotency.IdempotencyFilter;
import com.devnux.inventory.idempotency.IdempotencyStore;
import com.devnux.inventory.web.Problems;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, InventoryProperties props, IdempotencyStore idempotency) throws Exception {
        var keys = ApiKey.parse(props.apiKeys());
        return http
                .csrf(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ApiKeyFilter(keys), AnonymousAuthenticationFilter.class)
                // After authorization, so only authenticated, permitted writes reach the idempotency store.
                .addFilterAfter(new IdempotencyFilter(idempotency), AuthorizationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/openapi.yaml", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAuthority(ApiKey.READ)
                        .requestMatchers(HttpMethod.POST, "/api/v1/**").hasAuthority(ApiKey.WRITE)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/**").hasAuthority(ApiKey.WRITE)
                        .requestMatchers("/actuator/**").hasAuthority(ApiKey.READ)
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                Problems.write(res, HttpStatus.UNAUTHORIZED, "unauthorized", "Send a valid API key in the X-API-Key header"))
                        .accessDeniedHandler((req, res, ex) ->
                                Problems.write(res, HttpStatus.FORBIDDEN, "forbidden", "This API key is not allowed to do that")))
                .build();
    }
}
