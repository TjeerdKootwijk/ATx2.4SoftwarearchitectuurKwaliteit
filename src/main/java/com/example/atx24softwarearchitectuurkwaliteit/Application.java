package com.example.atx24softwarearchitectuurkwaliteit;

import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.example.atx24softwarearchitectuurkwaliteit.service.TenantService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(Application.class, args);
        
        // Initialize sample tenants for demo/testing
        TenantService tenantService = context.getBean(TenantService.class);
        initializeSampleTenants(tenantService);
    }

    private static void initializeSampleTenants(TenantService tenantService) {
        TenantConfiguration tenant1 = new TenantConfiguration("tenant-001", "Local OpenMRS Instance");
        tenant1.setOpenMrsBaseUrl("http://host.docker.internal:81/openmrs");
        tenant1.setOpenMrsUsername("admin");
        tenant1.setOpenMrsPassword("Admin123");
        tenant1.setWebhookSecret("webhook-secret-001-for-hmac");
        tenant1.setActive(true);
        tenantService.registerTenant(tenant1);

        TenantConfiguration tenant2 = new TenantConfiguration("tenant-002", "Demo Healthcare Organization");
        tenant2.setOpenMrsBaseUrl("http://host.docker.internal:81/openmrs");  // ← tenant2, niet tenant1
        tenant2.setOpenMrsUsername("admin");
        tenant2.setOpenMrsPassword("Admin123");
        tenant2.setWebhookSecret("webhook-secret-002-for-hmac");
        tenant2.setActive(true);
        tenantService.registerTenant(tenant2);
    }

}
