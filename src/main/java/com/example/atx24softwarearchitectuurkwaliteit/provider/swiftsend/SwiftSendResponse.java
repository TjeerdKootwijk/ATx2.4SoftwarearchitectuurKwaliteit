package com.example.atx24softwarearchitectuurkwaliteit.provider.swiftsend;

import java.util.List;

public class SwiftSendResponse {
    private boolean success;

    private String messageId;

    private List<String> failedRecipients;

    private String error;
}
