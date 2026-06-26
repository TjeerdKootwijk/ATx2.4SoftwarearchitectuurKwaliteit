package com.example.atx24softwarearchitectuurkwaliteit;

import com.example.atx24softwarearchitectuurkwaliteit.config.AesEncryptionConverter;
import com.example.atx24softwarearchitectuurkwaliteit.dao.jpa.TenantJpaRepository;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.TenantEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Beveiligingstesten — NFR5: gevoelige velden worden versleuteld opgeslagen (AES-256-GCM).
 *
 * Test 1–3: AesEncryptionConverter als pure unit, zonder Spring-context.
 * Test 4:   Integratie — TenantEntity opgeslagen via JPA; JDBC leest de ruwe
 *            databasewaarde terug zodat JPA-decryptie bewust wordt omzeild.
 *            Dit bewijst dat de plaintext nooit onversleuteld in de database staat.
 */
@SpringBootTest
class SecurityTest {

    @Autowired
    private TenantJpaRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── Hulpfunctie: converter met testsleutel (zelfde als application.properties) ─

    private AesEncryptionConverter converterMetTestSleutel() throws Exception {
        AesEncryptionConverter converter = new AesEncryptionConverter();
        Field keyField = AesEncryptionConverter.class.getDeclaredField("base64Key");
        keyField.setAccessible(true);
        keyField.set(converter, "EZLzg025mt6HbHesB93kP1X60RMYml1fsNY76P1ibpQ=");
        return converter;
    }

    // ── Unit tests ───────────────────────────────────────────────────────────────

    /**
     * De waarde die naar de database wordt geschreven mag nooit de originele
     * plaintext bevatten — ook niet als substring (NFR5).
     */
    @Test
    void versleuteling_slaat_plaintext_niet_op_in_database() throws Exception {
        AesEncryptionConverter converter = converterMetTestSleutel();
        String plaintext = "geheim-api-wachtwoord-12345";

        String opgeslagen = converter.convertToDatabaseColumn(plaintext);

        assertThat(opgeslagen)
                .as("De opgeslagen databasewaarde mag nooit de plaintext bevatten (NFR5)")
                .doesNotContain(plaintext);
    }

    /**
     * Versleuteling moet omkeerbaar zijn met dezelfde sleutel: decrypt(encrypt(x)) == x.
     * Dit garandeert dat de applicatie de credentials na opslag nog steeds kan lezen.
     */
    @Test
    void versleuteling_is_omkeerbaar_met_dezelfde_sleutel() throws Exception {
        AesEncryptionConverter converter = converterMetTestSleutel();
        String plaintext = "mijn-geheime-api-key-xyz";

        String versleuteld = converter.convertToDatabaseColumn(plaintext);
        String ontsleuteld = converter.convertToEntityAttribute(versleuteld);

        assertThat(ontsleuteld).isEqualTo(plaintext);
    }

    /**
     * AES-GCM gebruikt een willekeurige IV (Initialization Vector) per versleuteling.
     * Dezelfde plaintext levert dus elke keer een andere ciphertext op (IND-CPA security).
     * Dit voorkomt patroonherkenning in de database.
     */
    @Test
    void versleuteling_geeft_elke_keer_unieke_ciphertext_door_willekeurige_iv() throws Exception {
        AesEncryptionConverter converter = converterMetTestSleutel();
        String plaintext = "zelfde-wachtwoord";

        String ciphertext1 = converter.convertToDatabaseColumn(plaintext);
        String ciphertext2 = converter.convertToDatabaseColumn(plaintext);

        assertThat(ciphertext1)
                .as("Elke encryptie-aanroep moet een unieke ciphertext geven (willekeurige IV)")
                .isNotEqualTo(ciphertext2);
    }

    // ── Integratietest: JDBC omzeilt JPA-decryptie ───────────────────────────────

    /**
     * Sla een TenantEntity op via JPA en lees de ruwe waarde terug via JDBC —
     * zodat de automatische JPA-decryptie bewust wordt omzeild.
     *
     * Verwacht: de ruwe waarde in de database is Base64-gecodeerde ciphertext,
     * nooit de originele API-key in plaintext.
     *
     * Dit is het sterkste bewijs voor NFR5: zelfs als een aanvaller directe
     * toegang tot de database krijgt, zijn de credentials onleesbaar.
     */
    @Test
    void tenant_api_key_is_versleuteld_opgeslagen_in_database_kolom() {
        String geheimeApiKey = "super-geheim-api-key-12345";
        String tenantId = "security-test-tenant-" + System.currentTimeMillis();

        TenantEntity tenant = new TenantEntity();
        tenant.setTenantId(tenantId);
        tenant.setOrganizationName("Security Test Organisatie");
        tenant.setOpenMrsBaseUrl("http://test.local");
        tenant.setOpenMrsUsername("admin");
        tenant.setOpenMrsPassword("geheim-wachtwoord");
        tenant.setNotificationProvider("SWIFTSEND");
        tenant.setActive(false);
        tenant.setProviderApiKey(geheimeApiKey);
        tenantRepository.save(tenant);

        // Lees de ruwe kolomwaarde via JDBC — JPA-decryptie wordt hiermee omzeild
        String rawValue = jdbcTemplate.queryForObject(
                "SELECT provider_api_key FROM tenants WHERE tenant_id = ?",
                String.class,
                tenantId);

        assertThat(rawValue)
                .as("De ruwe databasewaarde mag nooit de plaintext-API-key bevatten (NFR5)")
                .doesNotContain(geheimeApiKey);
        assertThat(rawValue)
                .as("De opgeslagen waarde moet Base64-gecodeerde ciphertext zijn")
                .matches("[A-Za-z0-9+/=]+");

        tenantRepository.deleteById(tenantId);
    }
}
