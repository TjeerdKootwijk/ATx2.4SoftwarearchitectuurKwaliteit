package com.example.atx24softwarearchitectuurkwaliteit.messaging.retry;

import org.springframework.stereotype.Component;

/**
 * Exponential backoff delays:
 *   Poging 1 →  5s
 *   Poging 2 → 30s
 *   Poging 3 →  2 min
 *   Poging 4 →  5 min
 *   Poging 5 → 10 min
 *   Daarna   → dead-letter queue
 */
@Component
public class ExponentialBackoffRetryPolicy implements RetryPolicy {

    private static final long[] DELAYS_MS = {
            5_000L,
            30_000L,
            120_000L,
            300_000L,
            600_000L
    };

    @Override
    public boolean shouldRetry(int retryCount) {
        return retryCount < DELAYS_MS.length;
    }

    @Override
    public long getDelayMillis(int retryCount) {
        return DELAYS_MS[retryCount];
    }

    @Override
    public int getMaxRetries() {
        return DELAYS_MS.length;
    }
}
