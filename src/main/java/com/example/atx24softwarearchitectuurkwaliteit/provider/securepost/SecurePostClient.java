package com.example.atx24softwarearchitectuurkwaliteit.provider.securepost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class SecurePostClient {
    private static final Logger log = LoggerFactory.getLogger(SecurePostClient.class);

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final String studentGroup;

    private String cachedToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public SecurePostClient(WebClient.Builder webClientBuilder,
        @Value("${providers.base-url}") String baseUrl,
        @Value("${providers.securepost.client-id}") String clientId,
        @Value("${providers.securepost.client-secret}") String clientSecret,
        @Value("${providers.student-group}") String studentGroup) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.studentGroup = studentGroup;
    }

    public SecurePostResponse send(SecurePostRequest request) {
        log.info("Sending SecurePost message to recipient: {}", request.getRecipient());
        log.debug("Request body: {}", request);

        String token = getValidToken();
        if (token == null) {
            log.error("Could not obtain SecurePost JWT token");
            return null;
        }

        return webClient.post()
            .uri("/securepost/message")
            .header("Authorization", "Bearer " + token)
            .header("X-STUDENT-GROUP", studentGroup)
            .header("Content-Type", "application/json")
            .bodyValue(request)
            .retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse.bodyToMono(String.class).flatMap(body -> {
                    log.error("SecurePost API Error: {} - Response body: {}", clientResponse.statusCode(), body);
                    return Mono.error(new SecurePostException(
                            "SecurePost API Error: " + clientResponse.statusCode() + " body: " + body));
                })
            )
            .bodyToMono(SecurePostResponse.class)
            .doOnNext(response -> log.debug("SecurePost response: delivered={}, trackingId={}", response.isDelivered(), response.getTrackingId()))
            .onErrorResume(error -> {
                log.error("Error communicating with SecurePost API", error);
                return Mono.empty();
            })
            .block();
    }

    private synchronized String getValidToken() {
        if (cachedToken == null || Instant.now().isAfter(tokenExpiry.minusSeconds(30))) {
            log.info("Obtaining new SecurePost JWT token");
            SecurePostAuthResponse authResponse = webClient.post()
                .uri("/securepost/auth")
                .header("X-STUDENT-GROUP", studentGroup)
                .header("Content-Type", "application/json")
                .bodyValue(new SecurePostAuthRequest(clientId, clientSecret))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                    clientResponse.bodyToMono(String.class).flatMap(body -> {
                        log.error("SecurePost auth error: {} - {}", clientResponse.statusCode(), body);
                        return Mono.error(new SecurePostException("SecurePost auth failed: " + body));
                    })
                )
                .bodyToMono(SecurePostAuthResponse.class)
                .onErrorResume(error -> {
                    log.error("Error obtaining SecurePost token", error);
                    return Mono.empty();
                })
                .block();

            if (authResponse != null && authResponse.getAccessToken() != null) {
                cachedToken = authResponse.getAccessToken();
                tokenExpiry = Instant.now().plusSeconds(authResponse.getExpiresIn());
                log.info("SecurePost JWT token obtained, expires in {}s", authResponse.getExpiresIn());
            } else {
                cachedToken = null;
            }
        }
        return cachedToken;
    }
}
