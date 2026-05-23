package com.example.atx24softwarearchitectuurkwaliteit.provider;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;

public interface MessagingProvider {

    /**
     * Unieke naam van deze provider (hoofdletters, bijv. "SWIFTSEND").
     * De {@link MessagingProviderFactory} gebruikt deze waarde als sleutel.
     * Een nieuwe provider registreert zichzelf automatisch door deze methode
     * te implementeren — er hoeft niets elders in de code gewijzigd te worden.
     */
    String getProviderName();

    ProviderSendResult sendMessage(NotificationQueueMessage message);
}

