package com.example.atx24softwarearchitectuurkwaliteit.provider.swiftsend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@Component
public class SwiftSendClient {
    private static final Logger log = LoggerFactory.getLogger(SwiftSendClient.class);
    private final WebClient webClient;
    private final String baseUrl;
    private final String apiKey;
    private final String studentGroup;

    public SwiftSendClient(WebClient.Builder webClientBuilder,
        @Value("${providers.base-url}") String baseUrl,
        @Value("${providers.swiftsend.api-key}") String apiKey,
        @Value("${providers.student-group}") String studentGroup) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.studentGroup = studentGroup;
    }

    public SwiftSendResponse send(SwiftSendRequest swiftSendRequest) {
        log.info("Sending SwiftSend request to: {}/swiftsend", baseUrl);
        log.debug("Request body: {}", swiftSendRequest);
        log.debug("X-API-KEY: {}, X-STUDENT-GROUP: {}", apiKey, studentGroup);

        return webClient.post()
            .uri("/swiftsend")
            .header("X-API-KEY", apiKey)
            .header("X-STUDENT-GROUP", studentGroup)
            .header("Content-Type", "application/json")

            .bodyValue(swiftSendRequest)
            .retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse ->
                    clientResponse.bodyToMono(String.class).flatMap(body -> {
                        log.error("SwiftSend API Error: {} - Response body: {}", clientResponse.statusCode(), body);
                        return Mono.error(new SwiftSendExecption(
                                "SwiftSend API Error: "
                                + clientResponse.statusCode()
                                + " body: "
                                + body));
                    })
            )
            .bodyToMono(SwiftSendResponse.class)
            .doOnNext(response -> log.debug("SwiftSend response: {}", response))
            .onErrorResume(error -> {
                log.error("Error communicating with SwiftSend API", error);
                return Mono.empty();
            })
            .block();
    }
}
