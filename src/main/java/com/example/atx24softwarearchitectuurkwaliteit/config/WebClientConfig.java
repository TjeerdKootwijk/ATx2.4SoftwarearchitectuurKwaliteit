package com.example.atx24softwarearchitectuurkwaliteit.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

/**
 * NFR5 (transport): configures WebClient to enforce TLS 1.3 for all outbound
 * HTTPS connections. TLS 1.0, 1.1 and 1.2 are explicitly excluded.
 *
 * The dev profile trusts all certificates (InsecureTrustManagerFactory) so that
 * fake/stub servers with self-signed certs work out of the box. In production,
 * set SSL_TRUST_ALL=false and supply a proper trust store via JVM system properties
 * or a custom TrustManagerFactory.
 */
@Configuration
public class WebClientConfig {

    @Value("${ssl.trust-all:true}")
    private boolean trustAll;

    @Bean
    public WebClient webClient() throws SSLException {
        SslContextBuilder builder = SslContextBuilder.forClient()
                .sslProvider(SslProvider.JDK)
                .protocols("TLSv1.3");

        if (trustAll) {
            builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
        }

        SslContext sslContext = builder.build();

        HttpClient httpClient = HttpClient.create()
                .secure(spec -> spec.sslContext(sslContext));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
