package com.devnux.inventory.holds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Gives unpaid checkouts' stock back. Runs every {@code inventory.holds.expiry-interval} (30 seconds by default). */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);
    private final HoldService holds;

    public HoldExpiryJob(HoldService holds) {
        this.holds = holds;
    }

    @Scheduled(fixedDelayString = "${inventory.holds.expiry-interval}", initialDelayString = "${inventory.holds.expiry-interval}")
    public void run() {
        int total = 0, batch;
        do {
            batch = holds.expireDue();
            total += batch;
        } while (batch > 0);
        if (total > 0) log.info("Expired {} holds", total);
    }
}
