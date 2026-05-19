package com.example.atx24softwarearchitectuurkwaliteit.provider.securepost;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SecurePostAuthResponse {
    @JsonProperty("accessToken")
    private String accessToken;

    @JsonProperty("tokenType")
    private String tokenType;

    @JsonProperty("expiresIn")
    private int expiresIn;

    @JsonProperty("issuedAt")
    private String issuedAt;

    public String getAccessToken() { return accessToken; }
    public int getExpiresIn() { return expiresIn; }
}
