package com.devnux.inventory.holds;

import com.devnux.inventory.holds.Holds.Hold;
import com.devnux.inventory.holds.Holds.NewHold;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/holds")
public class HoldController {

    private final HoldService holds;

    public HoldController(HoldService holds) {
        this.holds = holds;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Hold create(@Valid @RequestBody NewHold body, Principal caller) {
        return holds.create(body, caller.getName());
    }

    @GetMapping("/{id}")
    Hold get(@PathVariable UUID id) {
        return holds.get(id);
    }

    @PostMapping("/{id}/confirm")
    Hold confirm(@PathVariable UUID id, Principal caller) {
        return holds.confirm(id, caller.getName());
    }

    @PostMapping("/{id}/release")
    Hold release(@PathVariable UUID id) {
        return holds.release(id);
    }
}
