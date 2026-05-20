package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

public class AsyncFlowException extends RuntimeException {
    public AsyncFlowException(String message) {
        super(message);
    }
    
    public AsyncFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
