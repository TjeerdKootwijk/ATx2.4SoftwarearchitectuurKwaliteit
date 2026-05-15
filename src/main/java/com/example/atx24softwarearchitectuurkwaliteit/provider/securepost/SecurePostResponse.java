package com.example.atx24softwarearchitectuurkwaliteit.provider.securepost;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SecurePostResponse {
    @JsonProperty("delivered")
    private boolean delivered;

    @JsonProperty("trackingId")
    private String trackingId;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("deliveryTimestamp")
    private String deliveryTimestamp;

    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }

    public String getTrackingId() { return trackingId; }
    public String getErrorMessage() { return errorMessage; }
    public String getDeliveryTimestamp() { return deliveryTimestamp; }
}
