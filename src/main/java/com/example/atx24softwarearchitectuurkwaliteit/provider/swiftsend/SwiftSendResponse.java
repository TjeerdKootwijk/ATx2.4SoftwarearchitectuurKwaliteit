package com.example.atx24softwarearchitectuurkwaliteit.provider.swiftsend;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SwiftSendResponse {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("messageId")
    private String messageId;

    @JsonProperty("failedRecipients")
    private List<String> failedRecipients;

    @JsonProperty("error")
    private String error;

    // Getters and setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public List<String> getFailedRecipients() {
        return failedRecipients;
    }

    public void setFailedRecipients(List<String> failedRecipients) {
        this.failedRecipients = failedRecipients;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
