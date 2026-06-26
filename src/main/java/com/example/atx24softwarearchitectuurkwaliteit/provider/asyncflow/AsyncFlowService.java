package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

public interface AsyncFlowService {
    AsyncFlowResponse send(AsyncFlowRequest request);

    /**
     * Vraagt de actuele afleverstatus op voor een eerder verstuurd bericht.
     * Geeft {@code null} terug als de status (tijdelijk) niet opgehaald kan worden.
     */
    AsyncFlowStatusResponse getStatus(String trackingId);
}