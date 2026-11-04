package com.devnux.inventory.stock;

import com.devnux.inventory.catalog.CatalogService;
import com.devnux.inventory.stock.Stock.Availability;
import com.devnux.inventory.stock.Stock.LowStockItem;
import com.devnux.inventory.stock.Stock.StockLevel;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class StockService {

    private final JdbcClient jdbc;
    private final CatalogService catalog;

    public StockService(JdbcClient jdbc, CatalogService catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    public Availability availability(String productCode) {
        long productId = catalog.productId(productCode);
        List<StockLevel> levels = jdbc.sql("""
                SELECT w.code, s.on_hand, s.reserved FROM stock_level s JOIN warehouse w ON w.id = s.warehouse_id
                 WHERE s.product_id = ? ORDER BY w.code""")
                .param(productId)
                .query((rs, n) -> new StockLevel(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(2) - rs.getInt(3)))
                .list();
        int onHand = levels.stream().mapToInt(StockLevel::onHand).sum();
        int reserved = levels.stream().mapToInt(StockLevel::reserved).sum();
        return new Availability(CatalogService.normalize(productCode), onHand, reserved, onHand - reserved, levels);
    }

    /** How many units are free to sell or move in one warehouse right now. */
    int available(long productId, long warehouseId) {
        return jdbc.sql("SELECT on_hand - reserved FROM stock_level WHERE product_id = ? AND warehouse_id = ?")
                .params(productId, warehouseId).query(Integer.class).optional().orElse(0);
    }

    public List<LowStockItem> lowStock() {
        return jdbc.sql("""
                SELECT p.code, p.name, p.reorder_point, coalesce(sum(s.on_hand - s.reserved), 0)::int AS available
                  FROM product p LEFT JOIN stock_level s ON s.product_id = p.id
                 WHERE p.reorder_point > 0
                 GROUP BY p.id
                HAVING coalesce(sum(s.on_hand - s.reserved), 0) <= p.reorder_point
                 ORDER BY p.reorder_point - coalesce(sum(s.on_hand - s.reserved), 0) DESC, p.code""")
                .query((rs, n) -> new LowStockItem(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                        Math.max(0, rs.getInt(3) - rs.getInt(4))))
                .list();
    }
}
