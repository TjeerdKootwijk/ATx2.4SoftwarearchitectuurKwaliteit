package com.example.atx24softwarearchitectuurkwaliteit.provider.legacylink;

public class LegacyLinkRequest {

    private String phoneNumber;
    private String messageText;
    private String senderIdentification;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getSenderIdentification() {
        return senderIdentification;
    }

    public void setSenderIdentification(String senderIdentification) {
        this.senderIdentification = senderIdentification;
    }
}