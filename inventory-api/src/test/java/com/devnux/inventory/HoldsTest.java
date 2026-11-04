package com.devnux.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devnux.inventory.holds.HoldService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** User story 3: hold stock at checkout. */
class HoldsTest extends ApiTestBase {

    @Autowired HoldService holds;

    @BeforeEach
    void stock() throws Exception {
        product("TENT-2P", 0);
        product("BOOT-TRL", 0);
        warehouse("PDX");
        receive("TENT-2P", "PDX", 5);
        receive("BOOT-TRL", "PDX", 1);
    }

    @Test
    void holdReducesAvailableAndConfirmShips() throws Exception {
        var created = hold("SO-5001", "PDX", """
                [{"productCode":"TENT-2P","quantity":2}]""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2));
        String id = holdId(created);
        assertThat(stock("TENT-2P", "PDX")).containsExactly(5, 2, 3);

        post("/holds/" + id + "/confirm", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
        assertThat(stock("TENT-2P", "PDX")).containsExactly(3, 0, 3);
        get("/products/{code}/movements", "TENT-2P")
                .andExpect(jsonPath("$[0].type").value("SHIP"))
                .andExpect(jsonPath("$[0].reference").value("SO-5001"))
                .andExpect(jsonPath("$[0].holdId").value(id));

        post("/holds/" + id + "/confirm", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("hold-not-active"));
        post("/holds/" + id + "/release", null).andExpect(status().isConflict());
    }

    @Test
    void releaseGivesTheStockBack() throws Exception {
        String id = holdId(hold("SO-5002", "PDX", """
                [{"productCode":"TENT-2P","quantity":4}]"""));
        post("/holds/" + id + "/release", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
        assertThat(stock("TENT-2P", "PDX")).containsExactly(5, 0, 5);
        get("/holds/{id}", id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
        get("/holds/{id}", "00000000-0000-0000-0000-000000000000").andExpect(status().isNotFound());
    }

    @Test
    void sc001_tenShoppersFiveUnitsExactlyFiveHolds() throws Exception {
        var pool = Executors.newFixedThreadPool(10);
        var start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String order = "SO-" + i;
            Callable<Integer> attempt = () -> {
                start.await();
                return hold(order, "PDX", """
                        [{"productCode":"TENT-2P","quantity":1}]""").andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(attempt));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (var f : results) statuses.add(f.get());
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(5);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(5);
        assertThat(stock("TENT-2P", "PDX")).containsExactly(5, 5, 0);
    }

    @Test
    void aMultiLineHoldIsAllOrNothing() throws Exception {
        hold("SO-6001", "PDX", """
                [{"productCode":"TENT-2P","quantity":2},{"productCode":"BOOT-TRL","quantity":3}]""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("insufficient-stock"))
                .andExpect(jsonPath("$.detail").value("Only 1 of BOOT-TRL available in PDX"));
        assertThat(stock("TENT-2P", "PDX")).containsExactly(5, 0, 5);
        assertThat(jdbc.sql("SELECT count(*) FROM hold").query(Long.class).single()).isZero();

        hold("SO-6002", "PDX", """
                [{"productCode":"BOOT-TRL","quantity":1},{"productCode":"TENT-2P","quantity":2}]""")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lines.length()").value(2));
    }

    @Test
    void expiredHoldsCannotBeConfirmedAndAreReleasedByTheJob() throws Exception {
        String id = holdId(hold("SO-7001", "PDX", """
                [{"productCode":"TENT-2P","quantity":3}]"""));
        jdbc.sql("UPDATE hold SET expires_at = now() - interval '1 second' WHERE id = ?::uuid").param(id).update();

        post("/holds/" + id + "/confirm", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("hold-not-active"));
        assertThat(holds.expireDue()).isEqualTo(1);
        get("/holds/{id}", id).andExpect(jsonPath("$.status").value("EXPIRED"));
        assertThat(stock("TENT-2P", "PDX")).containsExactly(5, 0, 5);
        assertThat(holds.expireDue()).isZero();
    }

    @Test
    void heldStockCannotBeShippedOrCountedAway() throws Exception {
        hold("SO-8001", "PDX", """
                [{"productCode":"TENT-2P","quantity":4}]""");
        movement("SHIP", "TENT-2P", "PDX", 2).andExpect(status().isConflict());
        post("/movements", """
                {"type":"ADJUST","productCode":"TENT-2P","warehouseCode":"PDX","quantity":-2,"reason":"Recount"}""")
                .andExpect(status().isConflict());
        post("/movements", """
                {"type":"ADJUST","productCode":"TENT-2P","warehouseCode":"PDX","quantity":-1,"reason":"Recount"}""")
                .andExpect(status().isCreated());
        assertThat(stock("TENT-2P", "PDX")).containsExactly(4, 4, 0);
    }

    @Test
    void badHoldRequestsAreRejected() throws Exception {
        hold("SO-9", "PDX", """
                [{"productCode":"TENT-2P","quantity":1},{"productCode":"tent-2p","quantity":1}]""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Each product may appear once in a hold"));
        postInvalid("/holds", """
                {"orderReference":"SO-9","warehouseCode":"PDX","lines":[{"productCode":"TENT-2P","quantity":1}],"ttlMinutes":500}""")
                .andExpect(status().isBadRequest());
        hold("SO-9", "NOPE", """
                [{"productCode":"TENT-2P","quantity":1}]""").andExpect(status().isNotFound());
    }
}
