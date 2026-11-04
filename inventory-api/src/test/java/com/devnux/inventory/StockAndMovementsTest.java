package com.devnux.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** User stories 1 and 2: know what can be sold, and record stock changes. */
class StockAndMovementsTest extends ApiTestBase {

    @BeforeEach
    void catalog() throws Exception {
        product("TENT-2P", 5);
        warehouse("PDX");
        warehouse("ATL");
    }

    @Test
    void us1_availabilityPerWarehouseAndInTotal() throws Exception {
        receive("TENT-2P", "PDX", 10).andExpect(status().isCreated());
        receive("TENT-2P", "ATL", 4).andExpect(status().isCreated());

        get("/products/{code}/stock", "tent-2p").andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("TENT-2P"))
                .andExpect(jsonPath("$.onHand").value(14))
                .andExpect(jsonPath("$.reserved").value(0))
                .andExpect(jsonPath("$.available").value(14))
                .andExpect(jsonPath("$.warehouses[0].warehouseCode").value("ATL"))
                .andExpect(jsonPath("$.warehouses[1].available").value(10));
    }

    @Test
    void us1_newProductHasZeroStockAndUnknownProductIsNotFound() throws Exception {
        get("/products/{code}/stock", "TENT-2P").andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(0)).andExpect(jsonPath("$.warehouses").isEmpty());
        get("/products/{code}/stock", "NOPE").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not-found"));
    }

    @Test
    void us2_receiveShipAndHistory() throws Exception {
        receive("TENT-2P", "PDX", 10);
        post("/movements", """
                {"type":"SHIP","productCode":"TENT-2P","warehouseCode":"PDX","quantity":3,"reference":"SO-5001"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SHIP"))
                .andExpect(jsonPath("$.lines[0].delta").value(-3))
                .andExpect(jsonPath("$.createdBy").value("storefront-dev"));

        assertThat(stock("TENT-2P", "PDX")).containsExactly(7, 0, 7);
        get("/products/{code}/movements", "TENT-2P").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("SHIP"))
                .andExpect(jsonPath("$[0].reference").value("SO-5001"))
                .andExpect(jsonPath("$[1].type").value("RECEIVE"));
    }

    @Test
    void us2_shippingMoreThanAvailableChangesNothing() throws Exception {
        receive("TENT-2P", "PDX", 7);
        movement("SHIP", "TENT-2P", "PDX", 9).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("insufficient-stock"));
        movement("SHIP", "TENT-2P", "ATL", 1).andExpect(status().isConflict()); // nothing ever received in ATL
        assertThat(stock("TENT-2P", "PDX")).containsExactly(7, 0, 7);
        get("/products/{code}/movements", "TENT-2P").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void us2_transferMovesBothSidesOrNeither() throws Exception {
        receive("TENT-2P", "PDX", 10);
        post("/movements", """
                {"type":"TRANSFER","productCode":"TENT-2P","fromWarehouseCode":"PDX","toWarehouseCode":"ATL","quantity":4}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(2));
        assertThat(stock("TENT-2P", "PDX")).containsExactly(6, 0, 6);
        assertThat(stock("TENT-2P", "ATL")).containsExactly(4, 0, 4);

        post("/movements", """
                {"type":"TRANSFER","productCode":"TENT-2P","fromWarehouseCode":"PDX","toWarehouseCode":"ATL","quantity":7}""")
                .andExpect(status().isConflict());
        post("/movements", """
                {"type":"TRANSFER","productCode":"TENT-2P","fromWarehouseCode":"PDX","toWarehouseCode":"PDX","quantity":1}""")
                .andExpect(status().isBadRequest());
        assertThat(stock("TENT-2P", "PDX")).containsExactly(6, 0, 6);
        assertThat(stock("TENT-2P", "ATL")).containsExactly(4, 0, 4);
    }

    @Test
    void us2_adjustmentsNeedAReasonAndCanBeNegative() throws Exception {
        receive("TENT-2P", "PDX", 10);
        post("/movements", """
                {"type":"ADJUST","productCode":"TENT-2P","warehouseCode":"PDX","quantity":-2}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("ADJUST needs a reason"));
        post("/movements", """
                {"type":"ADJUST","productCode":"TENT-2P","warehouseCode":"PDX","quantity":-2,"reason":"Cycle count"}""")
                .andExpect(status().isCreated());
        post("/movements", """
                {"type":"ADJUST","productCode":"TENT-2P","warehouseCode":"PDX","quantity":-9,"reason":"Too many"}""")
                .andExpect(status().isConflict());
        assertThat(stock("TENT-2P", "PDX")).containsExactly(8, 0, 8);
    }

    @Test
    void us2_quantitiesMustMakeSenseForTheType() throws Exception {
        movement("RECEIVE", "TENT-2P", "PDX", 0).andExpect(status().isBadRequest());
        movement("RECEIVE", "TENT-2P", "PDX", -5).andExpect(status().isBadRequest());
        post("/movements", """
                {"type":"RECEIVE","productCode":"TENT-2P","quantity":5}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("RECEIVE needs warehouseCode"));
        movement("RECEIVE", "TENT-2P", "NOPE", 5).andExpect(status().isNotFound());
        movement("RECEIVE", "NOPE", "PDX", 5).andExpect(status().isNotFound());
    }

    @Test
    void us2_retriesWithTheSameKeyApplyOnce() throws Exception {
        String receive = """
                {"type":"RECEIVE","productCode":"TENT-2P","warehouseCode":"PDX","quantity":10,"reference":"PO-1001"}""";
        String first = post("/movements", receive, "po-1001").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String replay = post("/movements", receive, "po-1001").andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();
        assertThat(replay).isEqualTo(first);
        assertThat(stock("TENT-2P", "PDX")).containsExactly(10, 0, 10);

        // A failed request isn't remembered: fix the problem and retry with the same key.
        String ship = """
                {"type":"SHIP","productCode":"TENT-2P","warehouseCode":"PDX","quantity":15}""";
        post("/movements", ship, "so-77").andExpect(status().isConflict());
        receive("TENT-2P", "PDX", 5);
        post("/movements", ship, "so-77").andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replayed"));
        assertThat(stock("TENT-2P", "PDX")).containsExactly(0, 0, 0);
    }

    @Test
    void sc002_stockAlwaysEqualsTheSumOfTheLedger() throws Exception {
        product("BOOT-TRL", 0);
        receive("TENT-2P", "PDX", 20);
        receive("BOOT-TRL", "ATL", 9);
        movement("SHIP", "TENT-2P", "PDX", 3);
        post("/movements", """
                {"type":"TRANSFER","productCode":"TENT-2P","fromWarehouseCode":"PDX","toWarehouseCode":"ATL","quantity":6}""");
        post("/movements", """
                {"type":"ADJUST","productCode":"BOOT-TRL","warehouseCode":"ATL","quantity":-1,"reason":"Damaged"}""");
        movement("SHIP", "TENT-2P", "PDX", 50); // rejected, must not leave a trace
        String holdBody = hold("SO-1", "ATL", """
                [{"productCode":"TENT-2P","quantity":2}]""").andReturn().getResponse().getContentAsString();
        post("/holds/" + com.jayway.jsonpath.JsonPath.read(holdBody, "$.id") + "/confirm", null);

        List<Map<String, Object>> mismatches = jdbc.sql("""
                SELECT s.product_id, s.warehouse_id, s.on_hand, coalesce(sum(l.delta), 0) AS ledger
                  FROM stock_level s
                  LEFT JOIN movement m ON m.product_id = s.product_id
                  LEFT JOIN movement_line l ON l.movement_id = m.id AND l.warehouse_id = s.warehouse_id
                 GROUP BY s.product_id, s.warehouse_id, s.on_hand
                HAVING s.on_hand <> coalesce(sum(l.delta), 0)""").query().listOfRows();
        assertThat(mismatches).isEmpty();
        assertThat(stock("TENT-2P", "PDX")).containsExactly(11, 0, 11);
        assertThat(stock("TENT-2P", "ATL")).containsExactly(4, 0, 4);
    }

    @Test
    void theLedgerCannotBeEdited() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.sql("DELETE FROM movement").update())
                .hasMessageContaining("append-only");
    }
}
