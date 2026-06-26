package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AsyncFlowClient implements AsyncFlowService {

    private static final Logger log = LoggerFactory.getLogger(AsyncFlowClient.class);

    private final WebClient webClient;
    private final String baseUrl;
    private final String apiKey;
    private final String studentGroup;

    public AsyncFlowClient(WebClient.Builder webClientBuilder,
                           @Value("${providers.base-url}") String baseUrl,
                           @Value("${providers.asyncflow.api-key}") String apiKey,
                           @Value("${providers.student-group}") String studentGroup) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.studentGroup = studentGroup;
    }

    @Override
    public AsyncFlowResponse send(AsyncFlowRequest request) {
        log.info("Sending AsyncFlow request to: {}/asyncflow", baseUrl);

        return webClient.post()
                .uri("/asyncflow")
                .header("X-API-KEY", apiKey)
                .header("X-STUDENT-GROUP", studentGroup)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class).flatMap(body -> {
                            log.error("AsyncFlow API Error: {} - Response body: {}",
                                    clientResponse.statusCode(), body);
                            return Mono.error(new AsyncFlowException(
                                    "AsyncFlow API Error: " + clientResponse.statusCode() +
                                            " body: " + body));
                        })
                )
                .bodyToMono(AsyncFlowResponse.class)
                .doOnNext(response -> log.debug("AsyncFlow response: {}", response))
                .onErrorResume(error -> {
                    log.error("Error communicating with AsyncFlow API", error);
                    return Mono.empty();
                })
                .block();
    }

    @Override
    public AsyncFlowStatusResponse getStatus(String trackingId) {
        return webClient.get()
                .uri("/asyncflow/{trackingId}", trackingId)
                .header("X-API-KEY", apiKey)
                .header("X-STUDENT-GROUP", studentGroup)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class).flatMap(body -> {
                            log.warn("AsyncFlow status lookup error: {} - body: {}",
                                    clientResponse.statusCode(), body);
                            return Mono.error(new AsyncFlowException(
                                    "AsyncFlow status error: " + clientResponse.statusCode()));
                        })
                )
                .bodyToMono(AsyncFlowStatusResponse.class)
                .onErrorResume(error -> {
                    log.warn("Kon AsyncFlow status niet ophalen voor trackingId={}: {}",
                            trackingId, error.getMessage());
                    return Mono.empty();
                })
                .block();
    }
}