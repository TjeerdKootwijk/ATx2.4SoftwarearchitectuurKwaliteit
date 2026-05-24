package com.example.atx24softwarearchitectuurkwaliteit.messaging.retry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExponentialBackoffRetryPolicyTest {

    private ExponentialBackoffRetryPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ExponentialBackoffRetryPolicy();
    }

    @Test
    void shouldRetry_returnsTrueBeforeMaxRetries() {
        assertThat(policy.shouldRetry(0)).isTrue();
        assertThat(policy.shouldRetry(2)).isTrue();
        assertThat(policy.shouldRetry(4)).isTrue();
    }

    @Test
    void shouldRetry_returnsFalseAtOrAfterMaxRetries() {
        int max = policy.getMaxRetries();

        assertThat(policy.shouldRetry(max)).isFalse();
        assertThat(policy.shouldRetry(max + 1)).isFalse();
    }

    @Test
    void getDelayMillis_firstRetryIs5Seconds() {
        assertThat(policy.getDelayMillis(0)).isEqualTo(5_000L);
    }

    @Test
    void getDelayMillis_delaysIncreaseWithEachRetry() {
        long prev = policy.getDelayMillis(0);
        for (int i = 1; i < policy.getMaxRetries(); i++) {
            long current = policy.getDelayMillis(i);
            assertThat(current).isGreaterThan(prev);
            prev = current;
        }
    }

    @Test
    void getMaxRetries_isFive() {
        assertThat(policy.getMaxRetries()).isEqualTo(5);
    }

    @Test
    void getDelayMillis_lastRetryIs10Minutes() {
        int lastIndex = policy.getMaxRetries() - 1;
        assertThat(policy.getDelayMillis(lastIndex)).isEqualTo(600_000L);
    }
}
