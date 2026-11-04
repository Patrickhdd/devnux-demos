package com.devnux.inventory;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** User story 4: restock before running out. */
class AlertsTest extends ApiTestBase {

    @Test
    void productsAtOrBelowTheirReorderPointAreListedByShortfall() throws Exception {
        product("TENT-2P", 5);
        product("BOOT-TRL", 10);
        product("PACK-28", 3);
        product("SOCKS", 0);
        warehouse("PDX");
        warehouse("ATL");
        receive("TENT-2P", "PDX", 2);
        receive("TENT-2P", "ATL", 2);   // 4 in total, reorder point 5
        receive("PACK-28", "PDX", 20);  // fine
        hold("SO-1", "PDX", """
                [{"productCode":"PACK-28","quantity":17}]""");  // 3 available, exactly the reorder point

        get("/alerts/low-stock").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].productCode").value("BOOT-TRL"))
                .andExpect(jsonPath("$[0].available").value(0))
                .andExpect(jsonPath("$[0].shortfall").value(10))
                .andExpect(jsonPath("$[1].productCode").value("TENT-2P"))
                .andExpect(jsonPath("$[1].shortfall").value(1))
                .andExpect(jsonPath("$[2].productCode").value("PACK-28"))
                .andExpect(jsonPath("$[2].shortfall").value(0));
    }
}
