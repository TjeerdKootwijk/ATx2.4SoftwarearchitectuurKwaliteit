package com.example.atx24softwarearchitectuurkwaliteit.provider.securepost;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SecurePostAuthRequest {
    @JsonProperty("clientId")
    private String clientId;

    @JsonProperty("clientSecret")
    private String clientSecret;

    public SecurePostAuthRequest(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
}
