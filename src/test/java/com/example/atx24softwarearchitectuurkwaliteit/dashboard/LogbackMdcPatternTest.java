package com.example.atx24softwarearchitectuurkwaliteit.dashboard;

import com.example.atx24softwarearchitectuurkwaliteit.service.logging.NotificationDiagnosticContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bewaakt dat de console-logpattern ({@code logback-spring.xml}) de diagnostische context
 * blijft tonen. Zonder deze velden in het pattern zou je lokaal wel de fout zien, maar niet
 * met welke ingevulde gegevens — precies de herleidbaarheid die we willen.
 */
class LogbackMdcPatternTest {

    private static final Path LOGBACK = Path.of("src/main/resources/logback-spring.xml");

    private String logback() throws IOException {
        assertTrue(Files.exists(LOGBACK), "logback-spring.xml ontbreekt: " + LOGBACK.toAbsolutePath());
        return Files.readString(LOGBACK);
    }

    @Test
    void pattern_exposesEveryDiagnosticContextKey() throws IOException {
        String xml = logback();
        for (String key : new String[]{
                NotificationDiagnosticContext.NOTIFICATION_ID,
                NotificationDiagnosticContext.TENANT_ID,
                NotificationDiagnosticContext.PROVIDER,
                NotificationDiagnosticContext.MESSAGE_TYPE,
                NotificationDiagnosticContext.ATTEMPT}) {
            assertTrue(xml.contains("%X{" + key),
                    "De logpattern moet MDC-sleutel '" + key + "' tonen (%X{" + key + "})");
        }
    }

    @Test
    void pattern_exposesTraceIdForTempoCorrelation() throws IOException {
        assertTrue(logback().contains("%X{trace_id"),
                "De logpattern moet trace_id tonen voor koppeling met de trace in Tempo");
    }
}
