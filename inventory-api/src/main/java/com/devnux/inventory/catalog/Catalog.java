package com.devnux.inventory.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request and response bodies for products and warehouses (contracts/openapi.yaml). */
public final class Catalog {
    private Catalog() {}

    public static final String CODE_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$";
    public static final String CODE_MESSAGE = "must be letters, digits, - or _, up to 40 characters";

    public record NewProduct(
            @NotNull @Pattern(regexp = CODE_PATTERN, message = CODE_MESSAGE) String code,
            @NotBlank @Size(max = 200) String name,
            @Min(0) Integer reorderPoint) {}

    public record ProductChanges(@Size(min = 1, max = 200) String name, @Min(0) Integer reorderPoint) {}

    public record Product(String code, String name, int reorderPoint, Instant createdAt) {}

    public record ProductPage(List<Product> items, int page, int size, long total) {}

    public record NewWarehouse(
            @NotNull @Pattern(regexp = CODE_PATTERN, message = CODE_MESSAGE) String code,
            @NotBlank @Size(max = 200) String name) {}

    public record Warehouse(String code, String name, Instant createdAt) {}
}
