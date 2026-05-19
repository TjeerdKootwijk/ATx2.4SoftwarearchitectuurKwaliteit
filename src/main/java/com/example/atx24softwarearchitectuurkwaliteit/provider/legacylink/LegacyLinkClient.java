package com.example.atx24softwarearchitectuurkwaliteit.provider.legacylink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class LegacyLinkClient {

    private static final Logger log = LoggerFactory.getLogger(LegacyLinkClient.class);

    private final WebClient webClient;
    private final String baseUrl;
    private final String studentGroup;
    private final String authorizationHeader;

    public LegacyLinkClient(WebClient.Builder webClientBuilder,
        @Value("${providers.base-url}") String baseUrl,
        @Value("${providers.legacylink.username}") String username,
        @Value("${providers.legacylink.password}") String password,
        @Value("${providers.student-group}") String studentGroup) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
        this.studentGroup = studentGroup;
        this.authorizationHeader = "Basic " + Base64.getEncoder().encodeToString(
            (username + ":" + password).getBytes(StandardCharsets.UTF_8)
        );
    }

    public LegacyLinkResponse send(LegacyLinkRequest request) {
        String xmlBody = LegacyLinkXmlMapper.toXml(request);

        log.info("Sending LegacyLink request to: {}/LegacyLink/SendSms", baseUrl);
        log.debug("Request body: {}", xmlBody);
        log.debug("Authorization: {}, X-STUDENT-GROUP: {}", authorizationHeader, studentGroup);

        String responseXml = webClient.post()
            .uri("/LegacyLink/SendSms")
            .header("Authorization", authorizationHeader)
            .header("X-STUDENT-GROUP", studentGroup)
            .header("Content-Type", "application/xml")
            .header("Accept", "application/xml")
            .bodyValue(xmlBody)
            .retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse.bodyToMono(String.class).flatMap(body -> {
                    log.error("LegacyLink API Error: {} - Response body: {}", clientResponse.statusCode(), body);
                    return Mono.error(new LegacyLinkException(
                        "LegacyLink API Error: " + clientResponse.statusCode() + " body: " + body));
                })
            )
            .bodyToMono(String.class)
            .doOnNext(response -> log.debug("LegacyLink response xml: {}", response))
            .onErrorResume(error -> {
                log.error("Error communicating with LegacyLink API", error);
                return Mono.empty();
            })
            .block();

        if (responseXml == null || responseXml.isBlank()) {
            return null;
        }

        return LegacyLinkXmlMapper.fromXml(responseXml);
    }
}