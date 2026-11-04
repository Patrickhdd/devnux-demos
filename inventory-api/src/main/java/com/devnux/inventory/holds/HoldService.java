package com.devnux.inventory.holds;

import com.devnux.inventory.catalog.CatalogService;
import com.devnux.inventory.config.InventoryProperties;
import com.devnux.inventory.holds.Holds.Hold;
import com.devnux.inventory.holds.Holds.HoldLine;
import com.devnux.inventory.holds.Holds.NewHold;
import com.devnux.inventory.holds.Holds.NewHoldLine;
import com.devnux.inventory.stock.MovementService;
import com.devnux.inventory.stock.MovementService.Line;
import com.devnux.inventory.stock.Stock.MovementType;
import com.devnux.inventory.web.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checkout holds: reserve, confirm into a shipment, release, expire. See data-model.md for the state machine. */
@Service
public class HoldService {

    private static final int EXPIRY_BATCH = 100;

    private final JdbcClient jdbc;
    private final CatalogService catalog;
    private final MovementService movements;
    private final InventoryProperties props;

    public HoldService(JdbcClient jdbc, CatalogService catalog, MovementService movements, InventoryProperties props) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.movements = movements;
        this.props = props;
    }

    private record Reserved(long productId, String productCode, int quantity) {}

    @Transactional
    public Hold create(NewHold request, String actor) {
        var seen = new HashSet<String>();
        for (NewHoldLine line : request.lines()) {
            if (!seen.add(CatalogService.normalize(line.productCode()))) throw ApiException.badRequest("Each product may appear once in a hold");
        }
        String warehouseCode = CatalogService.normalize(request.warehouseCode());
        long warehouseId = catalog.warehouseId(warehouseCode);
        // Reserve in product id order so overlapping holds lock rows in the same order (research.md R2).
        Map<Long, Reserved> lines = new TreeMap<>();
        for (NewHoldLine line : request.lines()) {
            String code = CatalogService.normalize(line.productCode());
            long productId = catalog.productId(code);
            lines.put(productId, new Reserved(productId, code, line.quantity()));
        }
        for (Reserved line : lines.values()) {
            int updated = jdbc.sql("""
                    UPDATE stock_level SET reserved = reserved + ?, updated_at = now()
                     WHERE product_id = ? AND warehouse_id = ? AND on_hand - reserved >= ?""")
                    .params(line.quantity(), line.productId(), warehouseId, line.quantity()).update();
            if (updated == 0) {
                int available = jdbc.sql("SELECT on_hand - reserved FROM stock_level WHERE product_id = ? AND warehouse_id = ?")
                        .params(line.productId(), warehouseId).query(Integer.class).optional().orElse(0);
                // Throwing rolls back the lines already reserved: all or nothing.
                throw ApiException.insufficientStock("Only " + available + " of " + line.productCode() + " available in " + warehouseCode);
            }
        }
        long ttlMinutes = request.ttlMinutes() != null ? request.ttlMinutes() : props.holds().defaultTtl().toMinutes();
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO hold (id, order_reference, warehouse_id, status, expires_at, created_by)
                VALUES (?, ?, ?, 'ACTIVE', now() + make_interval(mins => ?), ?)""")
                .params(id, request.orderReference().trim(), warehouseId, (int) ttlMinutes, actor).update();
        for (Reserved line : lines.values()) {
            jdbc.sql("INSERT INTO hold_line (hold_id, product_id, quantity) VALUES (?, ?, ?)")
                    .params(id, line.productId(), line.quantity()).update();
        }
        return get(id);
    }

    public Hold get(UUID id) {
        List<HoldLine> lines = jdbc.sql("""
                SELECT p.code, l.quantity FROM hold_line l JOIN product p ON p.id = l.product_id WHERE l.hold_id = ? ORDER BY p.code""")
                .param(id).query((rs, n) -> new HoldLine(rs.getString(1), rs.getInt(2))).list();
        return jdbc.sql("""
                SELECT h.id, h.order_reference, w.code, h.status, h.expires_at, h.created_at, h.closed_at
                  FROM hold h JOIN warehouse w ON w.id = h.warehouse_id WHERE h.id = ?""")
                .param(id)
                .query((rs, n) -> new Hold(rs.getObject("id", UUID.class), rs.getString("order_reference"), rs.getString("code"),
                        rs.getString("status"), lines, CatalogService.instant(rs, "expires_at"),
                        CatalogService.instant(rs, "created_at"), CatalogService.instant(rs, "closed_at")))
                .optional()
                .orElseThrow(() -> ApiException.notFound("Hold", id.toString()));
    }

    /** Turns the hold into a shipment: held units leave stock and a SHIP movement is recorded per line. */
    @Transactional
    public Hold confirm(UUID id, String actor) {
        var hold = jdbc.sql("""
                UPDATE hold SET status = 'CONFIRMED', closed_at = now()
                 WHERE id = ? AND status = 'ACTIVE' AND expires_at > now() RETURNING warehouse_id, order_reference""")
                .param(id).query((rs, n) -> new Object[] {rs.getLong(1), rs.getString(2)}).optional()
                .orElseThrow(() -> notActive(id));
        long warehouseId = (long) hold[0];
        String warehouseCode = jdbc.sql("SELECT code FROM warehouse WHERE id = ?").param(warehouseId).query(String.class).single();
        for (Reserved line : linesOf(id)) {
            unreserve(line, warehouseId);
            // Held units are no longer reserved, so the normal conditional SHIP update applies cleanly.
            movements.apply(MovementType.SHIP, line.productId(), List.of(new Line(warehouseId, warehouseCode, -line.quantity())),
                    null, (String) hold[1], id, actor);
        }
        return get(id);
    }

    @Transactional
    public Hold release(UUID id) {
        long warehouseId = jdbc.sql("""
                UPDATE hold SET status = 'RELEASED', closed_at = now() WHERE id = ? AND status = 'ACTIVE' RETURNING warehouse_id""")
                .param(id).query(Long.class).optional()
                .orElseThrow(() -> notActive(id));
        for (Reserved line : linesOf(id)) unreserve(line, warehouseId);
        return get(id);
    }

    /**
     * Expires one batch of overdue holds and returns how many. {@code SKIP LOCKED} lets several instances run this
     * at once without blocking each other (research.md R8).
     */
    @Transactional
    public int expireDue() {
        List<UUID> due = jdbc.sql("""
                SELECT id FROM hold WHERE status = 'ACTIVE' AND expires_at <= now()
                 ORDER BY expires_at LIMIT ? FOR UPDATE SKIP LOCKED""")
                .param(EXPIRY_BATCH).query(UUID.class).list();
        for (UUID id : due) {
            long warehouseId = jdbc.sql("UPDATE hold SET status = 'EXPIRED', closed_at = now() WHERE id = ? RETURNING warehouse_id")
                    .param(id).query(Long.class).single();
            for (Reserved line : linesOf(id)) unreserve(line, warehouseId);
        }
        return due.size();
    }

    private List<Reserved> linesOf(UUID holdId) {
        return jdbc.sql("""
                SELECT l.product_id, p.code, l.quantity FROM hold_line l JOIN product p ON p.id = l.product_id
                 WHERE l.hold_id = ? ORDER BY l.product_id""")
                .param(holdId).query((rs, n) -> new Reserved(rs.getLong(1), rs.getString(2), rs.getInt(3))).list();
    }

    private void unreserve(Reserved line, long warehouseId) {
        jdbc.sql("UPDATE stock_level SET reserved = reserved - ?, updated_at = now() WHERE product_id = ? AND warehouse_id = ?")
                .params(line.quantity(), line.productId(), warehouseId).update();
    }

    private ApiException notActive(UUID id) {
        Hold hold = get(id); // 404 if it doesn't exist
        String status = hold.status().equals("ACTIVE") ? "EXPIRED" : hold.status();
        return ApiException.holdNotActive(status);
    }
}
