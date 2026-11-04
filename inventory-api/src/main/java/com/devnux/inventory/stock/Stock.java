package com.devnux.inventory.stock;

import com.devnux.inventory.catalog.Catalog;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for stock, movements and alerts (contracts/openapi.yaml). */
public final class Stock {
    private Stock() {}

    public enum MovementType { RECEIVE, SHIP, ADJUST, TRANSFER }

    public record StockLevel(String warehouseCode, int onHand, int reserved, int available) {}

    public record Availability(String productCode, int onHand, int reserved, int available, List<StockLevel> warehouses) {}

    public record NewMovement(
            @NotNull MovementType type,
            @NotNull @Pattern(regexp = Catalog.CODE_PATTERN, message = Catalog.CODE_MESSAGE) String productCode,
            @Pattern(regexp = Catalog.CODE_PATTERN, message = Catalog.CODE_MESSAGE) String warehouseCode,
            @Pattern(regexp = Catalog.CODE_PATTERN, message = Catalog.CODE_MESSAGE) String fromWarehouseCode,
            @Pattern(regexp = Catalog.CODE_PATTERN, message = Catalog.CODE_MESSAGE) String toWarehouseCode,
            @NotNull @Min(-1_000_000) @Max(1_000_000) Integer quantity,
            @Size(max = 500) String reason,
            @Size(max = 100) String reference) {}

    public record MovementLine(String warehouseCode, int delta) {}

    public record Movement(long id, MovementType type, String productCode, List<MovementLine> lines, String reason,
                           String reference, UUID holdId, String createdBy, Instant createdAt) {}

    public record LowStockItem(String productCode, String name, int reorderPoint, int available, int shortfall) {}
}
