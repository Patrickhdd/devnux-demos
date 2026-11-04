package com.devnux.inventory.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inventory")
public record InventoryProperties(Holds holds, Idempotency idempotency, String apiKeys) {
    public record Holds(Duration defaultTtl, Duration expiryInterval) {}
    public record Idempotency(Duration retention) {}
}
