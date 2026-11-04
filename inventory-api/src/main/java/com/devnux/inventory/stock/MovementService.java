package com.devnux.inventory.stock;

import com.devnux.inventory.catalog.CatalogService;
import com.devnux.inventory.stock.Stock.Movement;
import com.devnux.inventory.stock.Stock.MovementLine;
import com.devnux.inventory.stock.Stock.MovementType;
import com.devnux.inventory.stock.Stock.NewMovement;
import com.devnux.inventory.web.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records movements in the ledger and applies them to stock levels in the same transaction. Decreases use
 * conditional updates, so stock can't go negative or below what is held, however many requests run at once
 * (research.md R1).
 */
@Service
public class MovementService {

    private final JdbcClient jdbc;
    private final CatalogService catalog;
    private final StockService stock;

    public MovementService(JdbcClient jdbc, CatalogService catalog, StockService stock) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.stock = stock;
    }

    /** One change to apply: positive deltas add stock, negative ones remove it. */
    public record Line(long warehouseId, String warehouseCode, int delta) {}

    @Transactional
    public Movement record(NewMovement m, String actor) {
        int q = m.quantity();
        switch (m.type()) {
            case RECEIVE, SHIP, TRANSFER -> { if (q <= 0) throw ApiException.badRequest("quantity must be positive for " + m.type()); }
            case ADJUST -> {
                if (q == 0) throw ApiException.badRequest("quantity can't be 0 for ADJUST");
                if (m.reason() == null || m.reason().isBlank()) throw ApiException.badRequest("ADJUST needs a reason");
            }
        }
        long productId = catalog.productId(m.productCode());

        List<Line> lines = new ArrayList<>();
        if (m.type() == MovementType.TRANSFER) {
            if (m.fromWarehouseCode() == null || m.toWarehouseCode() == null) {
                throw ApiException.badRequest("TRANSFER needs fromWarehouseCode and toWarehouseCode");
            }
            String from = CatalogService.normalize(m.fromWarehouseCode()), to = CatalogService.normalize(m.toWarehouseCode());
            if (from.equals(to)) throw ApiException.badRequest("TRANSFER needs two different warehouses");
            lines.add(new Line(catalog.warehouseId(from), from, -q));
            lines.add(new Line(catalog.warehouseId(to), to, q));
        } else {
            if (m.warehouseCode() == null) throw ApiException.badRequest(m.type() + " needs warehouseCode");
            String wh = CatalogService.normalize(m.warehouseCode());
            lines.add(new Line(catalog.warehouseId(wh), wh, m.type() == MovementType.SHIP ? -q : q));
        }
        return apply(m.type(), productId, lines, m.reason(), m.reference(), null, actor);
    }

    /** Applies lines to stock and writes the ledger entry. Used by holds too. Must run inside a transaction. */
    public Movement apply(MovementType type, long productId, List<Line> lines, String reason, String reference, UUID holdId, String actor) {
        // Lock rows in warehouse id order so opposite transfers can't deadlock (research.md R2).
        lines.stream().sorted((a, b) -> Long.compare(a.warehouseId(), b.warehouseId())).forEach(line -> {
            if (line.delta() > 0) {
                jdbc.sql("""
                        INSERT INTO stock_level (product_id, warehouse_id, on_hand) VALUES (?, ?, ?)
                        ON CONFLICT (product_id, warehouse_id) DO UPDATE SET on_hand = stock_level.on_hand + EXCLUDED.on_hand, updated_at = now()""")
                        .params(productId, line.warehouseId(), line.delta()).update();
            } else {
                int removed = -line.delta();
                int updated = jdbc.sql("""
                        UPDATE stock_level SET on_hand = on_hand - ?, updated_at = now()
                         WHERE product_id = ? AND warehouse_id = ? AND on_hand - reserved >= ?""")
                        .params(removed, productId, line.warehouseId(), removed).update();
                if (updated == 0) {
                    throw ApiException.insufficientStock("Only " + stock.available(productId, line.warehouseId()) + " of "
                            + productCode(productId) + " available in " + line.warehouseCode());
                }
            }
        });

        long id = jdbc.sql("""
                INSERT INTO movement (type, product_id, reason, reference, hold_id, created_by) VALUES (?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(type.name(), productId, blankToNull(reason), blankToNull(reference), holdId, actor)
                .query(Long.class).single();
        for (Line line : lines) {
            jdbc.sql("INSERT INTO movement_line (movement_id, warehouse_id, delta) VALUES (?, ?, ?)")
                    .params(id, line.warehouseId(), line.delta()).update();
        }
        return find(List.of(id)).getFirst();
    }

    public List<Movement> history(String productCode, int limit) {
        if (limit < 1 || limit > 500) throw ApiException.badRequest("limit must be 1 to 500");
        long productId = catalog.productId(productCode);
        List<Long> ids = jdbc.sql("SELECT id FROM movement WHERE product_id = ? ORDER BY id DESC LIMIT ?")
                .params(productId, limit).query(Long.class).list();
        return find(ids);
    }

    private List<Movement> find(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        Map<Long, List<MovementLine>> lines = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT l.movement_id, w.code, l.delta FROM movement_line l JOIN warehouse w ON w.id = l.warehouse_id
                 WHERE l.movement_id = ANY(?) ORDER BY l.id""")
                .param(ids.toArray(Long[]::new))
                .query(rs -> { lines.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new MovementLine(rs.getString(2), rs.getInt(3))); });
        Map<Long, Movement> byId = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT m.id, m.type, p.code, m.reason, m.reference, m.hold_id, m.created_by, m.created_at
                  FROM movement m JOIN product p ON p.id = m.product_id WHERE m.id = ANY(?)""")
                .param(ids.toArray(Long[]::new))
                .query(rs -> {
                    long id = rs.getLong("id");
                    byId.put(id, new Movement(id, MovementType.valueOf(rs.getString("type")), rs.getString("code"),
                            lines.getOrDefault(id, List.of()), rs.getString("reason"), rs.getString("reference"),
                            rs.getObject("hold_id", UUID.class), rs.getString("created_by"), CatalogService.instant(rs, "created_at")));
                });
        return ids.stream().map(byId::get).toList();
    }

    private String productCode(long productId) {
        return jdbc.sql("SELECT code FROM product WHERE id = ?").param(productId).query(String.class).single();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
