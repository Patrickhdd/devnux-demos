package com.devnux.inventory.catalog;

import com.devnux.inventory.catalog.Catalog.NewProduct;
import com.devnux.inventory.catalog.Catalog.NewWarehouse;
import com.devnux.inventory.catalog.Catalog.Product;
import com.devnux.inventory.catalog.Catalog.ProductChanges;
import com.devnux.inventory.catalog.Catalog.ProductPage;
import com.devnux.inventory.catalog.Catalog.Warehouse;
import com.devnux.inventory.web.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final JdbcClient jdbc;

    public CatalogService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }

    private static final RowMapper<Product> PRODUCT = (rs, n) ->
            new Product(rs.getString("code"), rs.getString("name"), rs.getInt("reorder_point"), instant(rs, "created_at"));
    private static final RowMapper<Warehouse> WAREHOUSE = (rs, n) ->
            new Warehouse(rs.getString("code"), rs.getString("name"), instant(rs, "created_at"));

    public Product createProduct(NewProduct p) {
        return jdbc.sql("INSERT INTO product (code, name, reorder_point) VALUES (?, ?, ?) RETURNING code, name, reorder_point, created_at")
                .params(normalize(p.code()), p.name().trim(), p.reorderPoint() == null ? 0 : p.reorderPoint())
                .query(PRODUCT).single();
    }

    public ProductPage listProducts(int page, int size) {
        if (page < 0 || size < 1 || size > 200) throw ApiException.badRequest("page must be 0 or more and size 1 to 200");
        List<Product> items = jdbc.sql("SELECT code, name, reorder_point, created_at FROM product ORDER BY code LIMIT ? OFFSET ?")
                .params(size, (long) page * size).query(PRODUCT).list();
        long total = jdbc.sql("SELECT count(*) FROM product").query(Long.class).single();
        return new ProductPage(items, page, size, total);
    }

    public Product getProduct(String code) {
        return jdbc.sql("SELECT code, name, reorder_point, created_at FROM product WHERE code = ?")
                .param(normalize(code)).query(PRODUCT).optional()
                .orElseThrow(() -> ApiException.notFound("Product", normalize(code)));
    }

    @Transactional
    public Product updateProduct(String code, ProductChanges changes) {
        if (changes.name() == null && changes.reorderPoint() == null) throw ApiException.badRequest("Send name, reorderPoint or both");
        return jdbc.sql("""
                UPDATE product SET name = coalesce(?, name), reorder_point = coalesce(?, reorder_point)
                 WHERE code = ? RETURNING code, name, reorder_point, created_at""")
                .params(changes.name() == null ? null : changes.name().trim(), changes.reorderPoint(), normalize(code))
                .query(PRODUCT).optional()
                .orElseThrow(() -> ApiException.notFound("Product", normalize(code)));
    }

    public Warehouse createWarehouse(NewWarehouse w) {
        return jdbc.sql("INSERT INTO warehouse (code, name) VALUES (?, ?) RETURNING code, name, created_at")
                .params(normalize(w.code()), w.name().trim()).query(WAREHOUSE).single();
    }

    public List<Warehouse> listWarehouses() {
        return jdbc.sql("SELECT code, name, created_at FROM warehouse ORDER BY code").query(WAREHOUSE).list();
    }

    public long productId(String code) {
        return jdbc.sql("SELECT id FROM product WHERE code = ?").param(normalize(code)).query(Long.class).optional()
                .orElseThrow(() -> ApiException.notFound("Product", normalize(code)));
    }

    public long warehouseId(String code) {
        return jdbc.sql("SELECT id FROM warehouse WHERE code = ?").param(normalize(code)).query(Long.class).optional()
                .orElseThrow(() -> ApiException.notFound("Warehouse", normalize(code)));
    }
}
