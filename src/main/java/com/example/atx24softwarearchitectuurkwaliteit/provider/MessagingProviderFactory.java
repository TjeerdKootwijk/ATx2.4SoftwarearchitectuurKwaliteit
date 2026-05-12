package com.example.atx24softwarearchitectuurkwaliteit.provider;
import org.springframework.stereotype.Component;

import java.nio.file.ProviderNotFoundException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class MessagingProviderFactory {

    private final Map<ProviderType, MessagingProvider> providers =
            new EnumMap<>(ProviderType.class);

    public MessagingProviderFactory(List<MessagingProvider> providers) {
        for(MessagingProvider provider : providers) {
            this.providers.put(provider.GetType(), provider);
        }
    }

    public MessagingProvider get(ProviderType providerType) {
        MessagingProvider provider = providers.get(providerType);

        if(provider == null) {
            throw new ProviderNotFoundException(
                    "No provider found for type " + providerType
            );
        }
        return provider;
    }

}
