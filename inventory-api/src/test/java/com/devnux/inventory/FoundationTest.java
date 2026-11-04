package com.devnux.inventory;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Security, catalog, errors and idempotency: the parts every story relies on. */
class FoundationTest extends ApiTestBase {

    @Test
    void callersNeedAKeyAndTheRightScope() throws Exception {
        perform(MockMvcRequestBuilders.get("/api/v1/products"), null, true).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("unauthorized"));
        perform(MockMvcRequestBuilders.get("/api/v1/products"), "not-a-real-key", true).andExpect(status().isUnauthorized());
        perform(MockMvcRequestBuilders.get("/api/v1/products"), READ_KEY, true).andExpect(status().isOk());
        perform(MockMvcRequestBuilders.post("/api/v1/warehouses").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"PDX","name":"Portland"}"""), READ_KEY, true)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void productCodesAreCaseInsensitiveAndUnique() throws Exception {
        post("/products", """
                {"code":"tent-2p","name":"Basecamp 2-person tent","reorderPoint":5}""")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("TENT-2P"));
        get("/products/{code}", "Tent-2P").andExpect(status().isOk()).andExpect(jsonPath("$.reorderPoint").value(5));
        post("/products", """
                {"code":"TENT-2p","name":"Duplicate"}""")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("duplicate"));
    }

    @Test
    void productsCanBeListedAndChanged() throws Exception {
        product("BOOT-TRL", 0);
        product("PACK-28", 0);
        patch("/products/PACK-28", """
                {"reorderPoint":12}""").andExpect(status().isOk()).andExpect(jsonPath("$.reorderPoint").value(12));
        get("/products?page=0&size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("BOOT-TRL")).andExpect(jsonPath("$.total").value(2));
        get("/products/{code}", "NOPE").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not-found"));
    }

    @Test
    void invalidInputGetsAProblemWithACode() throws Exception {
        postInvalid("/products", """
                {"code":"has spaces","name":""}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-failed"))
                .andExpect(jsonPath("$.detail", containsString("code must be letters")));
        postInvalid("/products", "{not json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid-request"));
    }

    @Test
    void sameIdempotencyKeyRunsOnceAndReplays() throws Exception {
        String body = """
                {"code":"PDX","name":"Portland"}""";
        post("/warehouses", body, "wh-1").andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replayed"));
        post("/warehouses", body, "wh-1").andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("PDX"));
        post("/warehouses", """
                {"code":"ATL","name":"Atlanta"}""", "wh-1")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("idempotency-key-reused"));
        get("/warehouses").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void contractAndHealthArePublic() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/openapi.yaml")).andExpect(status().isOk()).andExpect(content().string(containsString("openapi: 3.0.3")));
        mvc.perform(MockMvcRequestBuilders.get("/actuator/health")).andExpect(status().isOk());
    }
}
