package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AsyncFlowResponse {
    @JsonProperty("accepted")
    private boolean accepted;

    @JsonProperty("trackingId")
    private String trackingId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("submittedAt")
    private String submittedAt;

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(String submittedAt) {
        this.submittedAt = submittedAt;
    }

    @Override
    public String toString() {
        return "AsyncFlowResponse{" +
                "accepted=" + accepted +
                ", trackingId='" + trackingId + '\'' +
                ", message='" + message + '\'' +
                ", submittedAt='" + submittedAt + '\'' +
                '}';
    }
}