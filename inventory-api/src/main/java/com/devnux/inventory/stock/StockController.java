package com.devnux.inventory.stock;

import com.devnux.inventory.stock.Stock.Availability;
import com.devnux.inventory.stock.Stock.LowStockItem;
import com.devnux.inventory.stock.Stock.Movement;
import com.devnux.inventory.stock.Stock.NewMovement;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StockController {

    private final StockService stock;
    private final MovementService movements;

    public StockController(StockService stock, MovementService movements) {
        this.stock = stock;
        this.movements = movements;
    }

    @GetMapping("/products/{code}/stock")
    Availability stock(@PathVariable String code) {
        return stock.availability(code);
    }

    @GetMapping("/products/{code}/movements")
    List<Movement> history(@PathVariable String code, @RequestParam(defaultValue = "100") int limit) {
        return movements.history(code, limit);
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    Movement record(@Valid @RequestBody NewMovement body, Principal caller) {
        return movements.record(body, caller.getName());
    }

    @GetMapping("/alerts/low-stock")
    List<LowStockItem> lowStock() {
        return stock.lowStock();
    }
}
