package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Antwoord van het AsyncFlow status-endpoint {@code GET /asyncflow/{trackingId}}.
 *
 * AsyncFlow is asynchroon: bij versturen krijg je alleen {@code accepted}; de
 * werkelijke afleverstatus ({@code Completed} / {@code Failed}) komt pas later
 * beschikbaar via dit endpoint. Mogelijke statussen: Queued, Processing,
 * Completed, Failed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsyncFlowStatusResponse {

    @JsonProperty("trackingId")
    private String trackingId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("submittedAt")
    private String submittedAt;

    @JsonProperty("processedAt")
    private String processedAt;

    @JsonProperty("errorDetails")
    private String errorDetails;

    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }

    public String getProcessedAt() { return processedAt; }
    public void setProcessedAt(String processedAt) { this.processedAt = processedAt; }

    public String getErrorDetails() { return errorDetails; }
    public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }

    @Override
    public String toString() {
        return "AsyncFlowStatusResponse{trackingId='" + trackingId + "', status='" + status +
                "', processedAt='" + processedAt + "', errorDetails='" + errorDetails + "'}";
    }
}
