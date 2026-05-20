package com.example.atx24softwarearchitectuurkwaliteit;

import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.example.atx24softwarearchitectuurkwaliteit.service.TenantService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(Application.class, args);

        Environment env = context.getBean(Environment.class);
        TenantService tenantService = context.getBean(TenantService.class);

        initializeTenantsFromEnvironment(tenantService, env);
    }

    private static void initializeTenantsFromEnvironment(TenantService tenantService, Environment env) {

        // Tenant 001
        String baseUrl = env.getProperty("OPENMRS_BASE_URL");
        String username = env.getProperty("OPENMRS_USERNAME");
        String password = env.getProperty("OPENMRS_PASSWORD");

        if (baseUrl != null && username != null && password != null) {
            TenantConfiguration tenant1 = new TenantConfiguration("tenant-001", "Local OpenMRS Instance");
            tenant1.setOpenMrsBaseUrl(baseUrl);
            tenant1.setOpenMrsUsername(username);
            tenant1.setOpenMrsPassword(password);
            tenant1.setWebhookSecret(env.getProperty("WEBHOOK_SECRET_001", "default-webhook-secret-001"));
            tenant1.setActive(true);
            tenantService.registerTenant(tenant1);
            System.out.println("✅ Tenant-001 loaded successfully from .env");
        } else {
            System.out.println("⚠️  Tenant-001 not loaded: missing OPENMRS_ variables in .env");
        }

        // Tenant 002 (optioneel)
        String username2 = env.getProperty("OPENMRS_USERNAME_2");
        String password2 = env.getProperty("OPENMRS_PASSWORD_2");

        if (baseUrl != null && username2 != null && password2 != null) {
            TenantConfiguration tenant2 = new TenantConfiguration("tenant-002", "Demo Healthcare Organization");
            tenant2.setOpenMrsBaseUrl(baseUrl);
            tenant2.setOpenMrsUsername(username2);
            tenant2.setOpenMrsPassword(password2);
            tenant2.setWebhookSecret(env.getProperty("WEBHOOK_SECRET_002", "default-webhook-secret-002"));
            tenant2.setActive(true);
            tenantService.registerTenant(tenant2);
            System.out.println("✅ Tenant-002 loaded successfully from .env");
        }
    }
}