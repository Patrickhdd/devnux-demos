package com.devnux.inventory.catalog;

import com.devnux.inventory.catalog.Catalog.NewProduct;
import com.devnux.inventory.catalog.Catalog.NewWarehouse;
import com.devnux.inventory.catalog.Catalog.Product;
import com.devnux.inventory.catalog.Catalog.ProductChanges;
import com.devnux.inventory.catalog.Catalog.ProductPage;
import com.devnux.inventory.catalog.Catalog.Warehouse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    ProductPage products(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return catalog.listProducts(page, size);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    Product create(@Valid @RequestBody NewProduct body) {
        return catalog.createProduct(body);
    }

    @GetMapping("/products/{code}")
    Product product(@PathVariable String code) {
        return catalog.getProduct(code);
    }

    @PatchMapping("/products/{code}")
    Product update(@PathVariable String code, @Valid @RequestBody ProductChanges body) {
        return catalog.updateProduct(code, body);
    }

    @GetMapping("/warehouses")
    List<Warehouse> warehouses() {
        return catalog.listWarehouses();
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    Warehouse createWarehouse(@Valid @RequestBody NewWarehouse body) {
        return catalog.createWarehouse(body);
    }
}
