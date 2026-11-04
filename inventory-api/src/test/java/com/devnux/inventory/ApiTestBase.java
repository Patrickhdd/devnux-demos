package com.devnux.inventory;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Shared setup for the API tests. Every request and response goes through the OpenAPI validator, so a response that
 * drifts from {@code contracts/openapi.yaml} fails the build (constitution I and V, research.md R7).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class ApiTestBase {

    static final String CONTRACT_PATH = "specs/001-inventory-service/contracts/openapi.yaml";

    /** Validates request and response. */
    static final OpenApiInteractionValidator CONTRACT = OpenApiInteractionValidator.createFor(CONTRACT_PATH).build();

    /** For tests that deliberately send requests the contract forbids: only the response is checked. */
    static final OpenApiInteractionValidator RESPONSE_ONLY = OpenApiInteractionValidator.createFor(CONTRACT_PATH)
            .withLevelResolver(LevelResolver.create().withLevel("validation.request", ValidationReport.Level.IGNORE).build())
            .build();

    protected static final String WRITE_KEY = "dev-write-key";
    protected static final String READ_KEY = "dev-read-key";

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcClient jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.sql("TRUNCATE movement_line, movement, hold_line, hold, stock_level, product, warehouse, idempotency_record RESTART IDENTITY CASCADE")
                .update();
    }

    // ---- HTTP helpers ----

    protected ResultActions perform(MockHttpServletRequestBuilder request, String apiKey, boolean checkRequest) throws Exception {
        if (apiKey != null) request.header("X-API-Key", apiKey);
        return mvc.perform(request).andExpect(openApi().isValid(checkRequest ? CONTRACT : RESPONSE_ONLY));
    }

    protected ResultActions get(String path, Object... vars) throws Exception {
        return perform(MockMvcRequestBuilders.get("/api/v1" + path, vars), WRITE_KEY, true);
    }

    protected ResultActions post(String path, String json) throws Exception {
        return post(path, json, null);
    }

    protected ResultActions post(String path, String json, String idempotencyKey) throws Exception {
        var request = MockMvcRequestBuilders.post("/api/v1" + path);
        if (json != null) request.contentType(MediaType.APPLICATION_JSON).content(json);
        if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
        return perform(request, WRITE_KEY, true);
    }

    /** Sends a request that breaks the contract on purpose, to test the server's own validation. */
    protected ResultActions postInvalid(String path, String json) throws Exception {
        return perform(MockMvcRequestBuilders.post("/api/v1" + path).contentType(MediaType.APPLICATION_JSON).content(json), WRITE_KEY, false);
    }

    protected ResultActions patch(String path, String json) throws Exception {
        return perform(MockMvcRequestBuilders.patch("/api/v1" + path).contentType(MediaType.APPLICATION_JSON).content(json), WRITE_KEY, true);
    }

    // ---- Fixtures ----

    protected void product(String code, int reorderPoint) throws Exception {
        post("/products", """
                {"code":"%s","name":"Product %s","reorderPoint":%d}""".formatted(code, code, reorderPoint));
    }

    protected void warehouse(String code) throws Exception {
        post("/warehouses", """
                {"code":"%s","name":"Warehouse %s"}""".formatted(code, code));
    }

    protected ResultActions movement(String type, String product, String warehouse, int quantity) throws Exception {
        return post("/movements", """
                {"type":"%s","productCode":"%s","warehouseCode":"%s","quantity":%d,"reason":"test"}""".formatted(type, product, warehouse, quantity));
    }

    protected ResultActions receive(String product, String warehouse, int quantity) throws Exception {
        return movement("RECEIVE", product, warehouse, quantity);
    }

    protected ResultActions hold(String order, String warehouse, String linesJson) throws Exception {
        return post("/holds", """
                {"orderReference":"%s","warehouseCode":"%s","lines":%s}""".formatted(order, warehouse, linesJson));
    }

    protected String holdId(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** {onHand, reserved, available} for one product in one warehouse, read through the API. */
    protected int[] stock(String product, String warehouse) throws Exception {
        String body = get("/products/{code}/stock", product).andReturn().getResponse().getContentAsString();
        java.util.List<java.util.Map<String, Object>> levels = JsonPath.read(body, "$.warehouses[?(@.warehouseCode == '" + warehouse + "')]");
        if (levels.isEmpty()) return new int[] {0, 0, 0};
        var l = levels.get(0);
        return new int[] {(int) l.get("onHand"), (int) l.get("reserved"), (int) l.get("available")};
    }
}
