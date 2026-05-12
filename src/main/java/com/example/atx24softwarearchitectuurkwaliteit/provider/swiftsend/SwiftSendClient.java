package com.example.atx24softwarearchitectuurkwaliteit.provider.swiftsend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class SwiftSendClient {
    private final WebClient webClient;
    private final String apiKey;
    private final String studentGroup;

    public SwiftSendClient(WebClient.Builder webClientBuilder,
        @Value("${providers.base-url}") String baseUrlrl,
        @Value("${providers.SwiftSend.api-key}") String apiKey,
        @Value("${providers.student-group}") String studentGroup) {
        this.webClient = webClientBuilder.baseUrl(baseUrlrl).build();
        this.apiKey = apiKey;
        this.studentGroup = studentGroup;
    }

    public SwiftSendResponse send(SwiftSendRequest swiftSendRequest) {
        return webClient.post()
            .uri("/swiftsend")
            .header("X-API-KEY", apiKey)
            .header("X-STUDENT-GROUP", studentGroup)
            .header("Content-Type", "application/json")
            .bodyValue(swiftSendRequest)
            .retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse ->
                    clientResponse.bodyToMono(String.class).map(body -> new SwiftSendExecption(
                            "SwiftSend API Error: "
                            + clientResponse.statusCode()
                            + "body: "
                            + body

                    ))
            )
            .bodyToMono(SwiftSendResponse.class)
            .block();
    }



}
