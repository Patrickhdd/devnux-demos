package com.devnux.clinic.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clinic")
public record ClinicProperties(
        ZoneId zone,
        int bookingWindowDays,
        boolean seedDemoData,
        Jwt jwt,
        List<String> corsOrigins) {

    public record Jwt(String secret, Duration ttl) {}
}
