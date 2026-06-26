package com.example.atx24softwarearchitectuurkwaliteit.provider;

public class ProviderSendResult {

    private final boolean success;
    private final boolean pending;
    private final String providerMessageId;
    private final String status;
    private final String errorMessage;

    private ProviderSendResult(boolean success, boolean pending, String providerMessageId, String status, String errorMessage) {
        this.success = success;
        this.pending = pending;
        this.providerMessageId = providerMessageId;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public static ProviderSendResult success(String providerMessageId) {
        return new ProviderSendResult(true, false, providerMessageId, "SUCCESS", null);
    }

    public static ProviderSendResult send(String providerMessageId) {
        return new ProviderSendResult(true, false, providerMessageId, "SEND", null);
    }

    /**
     * Het bericht is door een asynchrone provider geaccepteerd maar nog niet
     * afgeleverd. {@code providerMessageId} bevat de trackingId waarmee de
     * afleverstatus later opgevraagd wordt. Niet als fout behandelen (geen retry),
     * maar ook nog niet als definitief succes loggen.
     */
    public static ProviderSendResult pending(String trackingId) {
        return new ProviderSendResult(true, true, trackingId, "PENDING", null);
    }

    public static ProviderSendResult error(String providerMessageId) {
        return new ProviderSendResult(false, false, providerMessageId, "ERROR", null);
    }

    public static ProviderSendResult error(String providerMessageId, String errorMessage) {
        return new ProviderSendResult(false, false, providerMessageId, "ERROR", errorMessage);
    }

    public boolean isSuccess(){
        return success;
    }

    /** True wanneer het bericht asynchroon geaccepteerd is en de status nog gepolld moet worden. */
    public boolean isPending(){
        return pending;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
