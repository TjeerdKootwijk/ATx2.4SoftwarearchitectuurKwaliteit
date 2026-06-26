package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * FMEA Scenario 2 - RPN 15 - HOOG - NFR7, NFR9
 *
 * Scenario: Netwerkpartitie tussen applicatie en RabbitMQ.
 * Effect:   Berichten kunnen niet worden gepubliceerd of gelezen.
 * Maatregel: Audit-log wordt opgeslagen VOOR de retry-poging via RabbitMQ.
 *            Zelfs als RabbitMQ onbereikbaar is, blijft de fout aantoonbaar
 *            in de database (Spring AMQP reconnect herstelt de verbinding).
 */
class Fmea02RabbitMqPartitieTest extends FmeaBaseTest {

    @Test
    void audit_log_opgeslagen_voor_retry_poging_ook_bij_rabbitmq_partitie() {
        stubProviderFout(503);

        // Simuleer RabbitMQ-verbindingsfout bij het inplannen van een retry
        doThrow(new AmqpException("Simulated RabbitMQ network partition"))
                .when(rabbitTemplate).convertAndSend(
                        any(String.class), any(String.class), any(Object.class),
                        any(org.springframework.amqp.core.MessagePostProcessor.class));

        String tenantId = "fmea02-tenant";
        try {
            consumer.consume(toAmqpMessage(swiftSendBericht(tenantId)));
        } catch (AmqpException ignored) {
            // AmqpException propageert correct naar de AMQP-listener die de bericht nack't
        }

        // Audit-log moet zijn opgeslagen VOOR de mislukte retry-poging
        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId(tenantId);
        assertThat(logs)
                .as("Audit-log moet aanwezig zijn ook als RabbitMQ onbereikbaar is bij retry")
                .hasSize(1);
        assertThat(logs.get(0).getStatus())
                .isEqualTo("FAILED");
    }
}
