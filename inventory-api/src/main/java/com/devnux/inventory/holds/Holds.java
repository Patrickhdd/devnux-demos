package com.devnux.inventory.holds;

import com.devnux.inventory.catalog.Catalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for holds (contracts/openapi.yaml). */
public final class Holds {
    private Holds() {}

    public record NewHoldLine(
            @NotNull @Pattern(regexp = Catalog.CODE_PATTERN, message = Catalog.CODE_MESSAGE) String productCode,
            @NotNull @Min(1) @Max(100_000) Integer quantity) {}

    public record NewHold(
            @NotBlank @Size(max = 100) String orderReference,
            @NotNull @Pattern(regexp = Catalog.CODE_PATTERN, message = Catalog.CODE_MESSAGE) String warehouseCode,
            @NotEmpty @Size(max = 100) List<@Valid @NotNull NewHoldLine> lines,
            @Min(1) @Max(120) Integer ttlMinutes) {}

    public record HoldLine(String productCode, int quantity) {}

    public record Hold(UUID id, String orderReference, String warehouseCode, String status, List<HoldLine> lines,
                       Instant expiresAt, Instant createdAt, Instant closedAt) {}
}
