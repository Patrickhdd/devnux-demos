package com.devnux.inventory.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** One calling system, for example the storefront or a warehouse scanner. */
public record ApiKey(String name, String key, Set<String> authorities) {

    public static final String READ = "inventory:read";
    public static final String WRITE = "inventory:write";

    /** Parses comma-separated {@code name:key:read+write} entries. */
    public static List<ApiKey> parse(String config) {
        if (config == null || config.isBlank()) return List.of();
        return Arrays.stream(config.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(entry -> {
                    String[] parts = entry.split(":");
                    if (parts.length != 3 || parts[0].isBlank() || parts[1].length() < 8) {
                        throw new IllegalArgumentException("API key entries look like name:key:read+write, with keys of 8+ characters");
                    }
                    Set<String> authorities = Arrays.stream(parts[2].split("\\+"))
                            .map(scope -> switch (scope.trim()) {
                                case "read" -> READ;
                                case "write" -> WRITE;
                                default -> throw new IllegalArgumentException("Unknown scope '" + scope + "', use read or write");
                            })
                            .collect(Collectors.toUnmodifiableSet());
                    return new ApiKey(parts[0].trim(), parts[1].trim(), authorities);
                })
                .toList();
    }
}
