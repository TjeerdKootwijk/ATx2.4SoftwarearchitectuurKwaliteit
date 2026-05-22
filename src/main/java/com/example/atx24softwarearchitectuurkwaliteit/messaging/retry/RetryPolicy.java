package com.example.atx24softwarearchitectuurkwaliteit.messaging.retry;

public interface RetryPolicy {
    boolean shouldRetry(int retryCount);
    long getDelayMillis(int retryCount);
    int getMaxRetries();
}
