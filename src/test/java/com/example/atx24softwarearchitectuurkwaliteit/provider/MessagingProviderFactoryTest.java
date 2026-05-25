package com.example.atx24softwarearchitectuurkwaliteit.provider;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.ProviderNotFoundException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessagingProviderFactoryTest {

    private MessagingProvider stubProvider(String name) {
        MessagingProvider provider = mock(MessagingProvider.class);
        when(provider.getProviderName()).thenReturn(name);
        return provider;
    }

    @Test
    void get_returnsCorrectProviderByName() {
        MessagingProvider swiftSend = stubProvider(ProviderType.SWIFTSEND);
        MessagingProviderFactory factory = new MessagingProviderFactory(List.of(swiftSend));

        MessagingProvider result = factory.get(ProviderType.SWIFTSEND);

        assertThat(result).isSameAs(swiftSend);
    }

    @Test
    void get_isCaseInsensitive() {
        MessagingProvider legacyLink = stubProvider(ProviderType.LEGACYLINK);
        MessagingProviderFactory factory = new MessagingProviderFactory(List.of(legacyLink));

        assertThat(factory.get("legacylink")).isSameAs(legacyLink);
        assertThat(factory.get("LegacyLink")).isSameAs(legacyLink);
        assertThat(factory.get("LEGACYLINK")).isSameAs(legacyLink);
    }

    @Test
    void get_throwsProviderNotFoundExceptionForUnknownProvider() {
        MessagingProviderFactory factory = new MessagingProviderFactory(List.of(
                stubProvider(ProviderType.SWIFTSEND)
        ));

        assertThatThrownBy(() -> factory.get("UNKNOWN_PROVIDER"))
                .isInstanceOf(ProviderNotFoundException.class)
                .hasMessageContaining("UNKNOWN_PROVIDER");
    }

    @Test
    void factory_registersAllProvidersFromConstructor() {
        MessagingProvider swiftSend = stubProvider(ProviderType.SWIFTSEND);
        MessagingProvider asyncFlow = stubProvider(ProviderType.ASYNCFLOW);
        MessagingProvider legacyLink = stubProvider(ProviderType.LEGACYLINK);
        MessagingProvider securePost = stubProvider(ProviderType.SECUREPOST);

        MessagingProviderFactory factory = new MessagingProviderFactory(
                List.of(swiftSend, asyncFlow, legacyLink, securePost)
        );

        assertThat(factory.get(ProviderType.SWIFTSEND)).isSameAs(swiftSend);
        assertThat(factory.get(ProviderType.ASYNCFLOW)).isSameAs(asyncFlow);
        assertThat(factory.get(ProviderType.LEGACYLINK)).isSameAs(legacyLink);
        assertThat(factory.get(ProviderType.SECUREPOST)).isSameAs(securePost);
    }

    @Test
    void factory_newProviderRegisteredWithoutModifyingFactory() {
        // OCP-bewijs: een nieuwe provider werkt direct zonder aanpassing van de factory
        MessagingProvider customProvider = new MessagingProvider() {
            @Override
            public String getProviderName() { return "MYPROVIDER"; }

            @Override
            public ProviderSendResult sendMessage(NotificationQueueMessage message) {
                return ProviderSendResult.send("custom-id");
            }
        };

        MessagingProviderFactory factory = new MessagingProviderFactory(List.of(customProvider));

        assertThat(factory.get("MYPROVIDER")).isSameAs(customProvider);
    }
}
