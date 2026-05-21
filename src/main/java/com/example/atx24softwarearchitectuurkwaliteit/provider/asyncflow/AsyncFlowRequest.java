package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AsyncFlowRequest {

    @JsonProperty("destination")
    private String recipient;

    @JsonProperty("content")
    private String message;

    @JsonProperty("priority")
    private String priority;

    @JsonProperty("template")
    private String template;

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    @Override
    public String toString() {
        return "AsyncFlowRequest{" +
                "recipient='" + recipient + '\'' +
                ", message='" + message + '\'' +
                ", priority='" + priority + '\'' +
                ", template='" + template + '\'' +
                '}';
    }
}