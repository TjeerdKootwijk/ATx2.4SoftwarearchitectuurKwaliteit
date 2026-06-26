package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 18 - RPN 5 - LAAG - NFR5, NFR12
 *
 * Scenario: Patientgegevens verschijnen in RabbitMQ dead-letter queue (plaintext).
 * Effect:   Gevoelige data leesbaar voor beheerders met queue-toegang.
 * Maatregel: NotificationQueueMessage bevat geen directe patientidentificatoren.
 *            Alleen appointmentUuid, tenantId, changeType en telefoonnummer
 *            zijn aanwezig (geen naam, BSN, geboortedatum of adres).
 */
class Fmea18PatientDataInDlqTest extends FmeaBaseTest {

    @Test
    void queue_bericht_bevat_geen_patient_naam_velden() {
        Set<String> veldnamen = getVeldnamen();

        List<String> naamVelden = List.of("patientname", "naam", "name", "firstname",
                "lastname", "voornaam", "achternaam", "fullname");

        for (String verboden : naamVelden) {
            assertThat(veldnamen)
                    .as("NotificationQueueMessage mag geen veld '%s' bevatten (AVG/GDPR)", verboden)
                    .doesNotContain(verboden);
        }
    }

    @Test
    void queue_bericht_bevat_geen_bsn_of_identificatienummer_velden() {
        Set<String> veldnamen = getVeldnamen();

        List<String> bsnVelden = List.of("bsn", "burgerservicenummer", "ssn",
                "socialsecuritynumber", "patientid", "nationalid");

        for (String verboden : bsnVelden) {
            assertThat(veldnamen)
                    .as("NotificationQueueMessage mag geen veld '%s' bevatten (AVG/GDPR)", verboden)
                    .doesNotContain(verboden);
        }
    }

    @Test
    void queue_bericht_bevat_geen_geboortedatum_of_adres_velden() {
        Set<String> veldnamen = getVeldnamen();

        List<String> privacyVelden = List.of("dateofbirth", "geboortedatum", "birthdate",
                "address", "adres", "email", "postcode", "zipcode");

        for (String verboden : privacyVelden) {
            assertThat(veldnamen)
                    .as("NotificationQueueMessage mag geen veld '%s' bevatten (AVG/GDPR)", verboden)
                    .doesNotContain(verboden);
        }
    }

    @Test
    void queue_bericht_bevat_verplichte_audit_velden() {
        Set<String> veldnamen = getVeldnamen();

        assertThat(veldnamen)
                .as("Bericht moet notificationId en tenantId bevatten voor audit (FR2)")
                .contains("notificationid", "tenantid");
    }

    private Set<String> getVeldnamen() {
        return Arrays.stream(NotificationQueueMessage.class.getDeclaredFields())
                .map(Field::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}
