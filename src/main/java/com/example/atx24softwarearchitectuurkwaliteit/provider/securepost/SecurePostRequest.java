package com.example.atx24softwarearchitectuurkwaliteit.provider.securepost;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurePostRequest {
    @JsonProperty("format")
    private String format;

    @JsonProperty("recipient")
    private String recipient;

    @JsonProperty("body")
    private String body;

    @JsonProperty("subject")
    private String subject;

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    @Override
    public String toString() {
        return "SecurePostRequest{format='" + format + "', recipient='" + recipient + "', body='" + body + "'}";
    }
}
