package com.example.atx24softwarearchitectuurkwaliteit.provider;

import org.springframework.stereotype.Component;

import java.nio.file.ProviderNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verzamelt alle {@link MessagingProvider}-implementaties die Spring detecteert
 * en slaat ze op in een map op naam.
 *
 * OCP: een nieuwe provider wordt automatisch geregistreerd zodra Spring de
 * bijbehorende {@code @Service}-klasse aantreft — deze factory hoeft nooit
 * gewijzigd te worden.
 */
@Component
public class MessagingProviderFactory {

    private final Map<String, MessagingProvider> providers = new HashMap<>();

    public MessagingProviderFactory(List<MessagingProvider> providers) {
        for (MessagingProvider provider : providers) {
            this.providers.put(provider.getProviderName().toUpperCase(), provider);
        }
    }

    public MessagingProvider get(String providerName) {
        MessagingProvider provider = providers.get(providerName.toUpperCase());

        if (provider == null) {
            throw new ProviderNotFoundException(
                    "No provider registered for name '" + providerName + "'. " +
                    "Known providers: " + providers.keySet()
            );
        }
        return provider;
    }
}
