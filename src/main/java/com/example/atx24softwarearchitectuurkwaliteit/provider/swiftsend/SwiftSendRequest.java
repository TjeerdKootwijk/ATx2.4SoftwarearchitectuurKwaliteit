package com.example.atx24softwarearchitectuurkwaliteit.provider.swiftsend;

//import java.awt.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SwiftSendRequest {
    @JsonProperty("type")
    private String type = "SMS";

    @JsonProperty("recipients")
    private List<String> recipients;

    @JsonProperty("content")
    private String content;

    // Getters and setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "SwiftSendRequest{" +
                "type=" + type +
                ", recipients=" + recipients +
                ", content='" + content + '\'' +
                '}';
    }
}
