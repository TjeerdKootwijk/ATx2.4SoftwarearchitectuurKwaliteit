package com.example.atx24softwarearchitectuurkwaliteit.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

/**
 * Configures the shared WebClient bean used for all outbound HTTP calls.
 *
 * NFR: TLS 1.3 is enforced on every outbound connection.
 * Older protocol versions (TLS 1.0, 1.1, 1.2) are explicitly disabled
 * by restricting the enabled protocols on the SSLEngine to TLSv1.3 only.
 *
 * The JDK SSL provider is used — Java 21 ships with full TLS 1.3 support
 * out of the box, so no native (BoringSSL / OpenSSL) library is required.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() throws SSLException {
        SslContext sslContext = SslContextBuilder
                .forClient()
                .sslProvider(SslProvider.JDK)
                .build();

        HttpClient httpClient = HttpClient.create()
                .secure(spec -> spec
                        .sslContext(sslContext)
                        .handlerConfigurator(sslHandler -> {
                            // Restrict to TLS 1.3 — older versions are not allowed.
                            sslHandler.engine().setEnabledProtocols(new String[]{"TLSv1.3"});
                        }));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
