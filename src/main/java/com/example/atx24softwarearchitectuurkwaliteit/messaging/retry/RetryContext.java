package com.example.atx24softwarearchitectuurkwaliteit.messaging.retry;

import org.springframework.amqp.core.MessageProperties;

import java.time.Instant;

public record RetryContext(int retryCount, Instant firstFailedAt) {

    static final String HEADER_RETRY_COUNT = "x-notification-retry-count";
    static final String HEADER_FIRST_FAILED_AT = "x-notification-first-failed-at";

    public static RetryContext fromMessageProperties(MessageProperties props) {
        Object countHeader = props.getHeader(HEADER_RETRY_COUNT);
        Object timeHeader = props.getHeader(HEADER_FIRST_FAILED_AT);

        int count = countHeader instanceof Integer i ? i : 0;
        Instant firstFailed = timeHeader instanceof String s ? Instant.parse(s) : Instant.now();
        return new RetryContext(count, firstFailed);
    }
}
