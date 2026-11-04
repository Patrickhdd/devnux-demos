package com.devnux.inventory.idempotency;

import com.devnux.inventory.config.InventoryProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Remembers write requests by (client, Idempotency-Key). See research.md R4. */
@Component
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);

    public record Stored(String requestHash, String state, Integer status, String contentType, String body) {}

    private final JdbcClient jdbc;
    private final InventoryProperties props;

    public IdempotencyStore(JdbcClient jdbc, InventoryProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    /** Claims the key. Returns false if it is already taken (and not expired). */
    public boolean claim(String client, String key, String requestHash) {
        jdbc.sql("DELETE FROM idempotency_record WHERE client = ? AND key = ? AND created_at < now() - make_interval(secs => ?)")
                .params(client, key, props.idempotency().retention().toSeconds()).update();
        return jdbc.sql("""
                INSERT INTO idempotency_record (client, key, request_hash, state) VALUES (?, ?, ?, 'IN_PROGRESS')
                ON CONFLICT (client, key) DO NOTHING""")
                .params(client, key, requestHash).update() == 1;
    }

    public Optional<Stored> find(String client, String key) {
        return jdbc.sql("SELECT request_hash, state, response_status, response_type, response_body FROM idempotency_record WHERE client = ? AND key = ?")
                .params(client, key)
                .query((rs, n) -> new Stored(rs.getString(1), rs.getString(2), (Integer) rs.getObject(3), rs.getString(4), rs.getString(5)))
                .optional();
    }

    public void complete(String client, String key, int status, String contentType, String body) {
        jdbc.sql("UPDATE idempotency_record SET state = 'COMPLETED', response_status = ?, response_type = ?, response_body = ? WHERE client = ? AND key = ?")
                .params(status, contentType, body, client, key).update();
    }

    public void forget(String client, String key) {
        jdbc.sql("DELETE FROM idempotency_record WHERE client = ? AND key = ?").params(client, key).update();
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    public void purgeExpired() {
        int n = jdbc.sql("DELETE FROM idempotency_record WHERE created_at < now() - make_interval(secs => ?)")
                .param(props.idempotency().retention().toSeconds()).update();
        if (n > 0) log.info("Purged {} expired idempotency records", n);
    }
}
