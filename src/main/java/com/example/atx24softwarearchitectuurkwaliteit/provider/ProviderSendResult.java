package com.example.atx24softwarearchitectuurkwaliteit.provider;

public class ProviderSendResult {

    private final boolean success;
    private final String providerMessageId;
    private final String status;
    private final String errorMessage;

    private ProviderSendResult(boolean success, String providerMessageId, String status, String errorMessage) {
        this.success = success;
        this.providerMessageId = providerMessageId;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public static ProviderSendResult success(String providerMessageId) {
        return new ProviderSendResult(true, providerMessageId, "SUCCESS", null);
    }

    public static ProviderSendResult send(String providerMessageId) {
        return new ProviderSendResult(true, providerMessageId, "SEND", null);
    }

    public static ProviderSendResult error(String providerMessageId) {
        return new ProviderSendResult(false, providerMessageId, "ERROR", null);
    }

    public static ProviderSendResult error(String providerMessageId, String errorMessage) {
        return new ProviderSendResult(false, providerMessageId, "ERROR", errorMessage);
    }

    public boolean isSuccess(){
        return success;
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
