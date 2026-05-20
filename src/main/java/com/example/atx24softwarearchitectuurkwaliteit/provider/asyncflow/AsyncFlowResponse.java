package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AsyncFlowResponse {
    @JsonProperty("queued")
    private boolean queued;

    @JsonProperty("queueId")
    private String queueId;

    @JsonProperty("estimatedDelivery")
    private String estimatedDelivery;

    @JsonProperty("status")
    private String status;

    @JsonProperty("errorCode")
    private String errorCode;

    @JsonProperty("errorMessage")
    private String errorMessage;

    public boolean isQueued() { 
        return queued; 
    }
    
    public void setQueued(boolean queued) { 
        this.queued = queued; 
    }

    public String getQueueId() { 
        return queueId; 
    }
    
    public void setQueueId(String queueId) { 
        this.queueId = queueId; 
    }

    public String getEstimatedDelivery() { 
        return estimatedDelivery; 
    }
    
    public void setEstimatedDelivery(String estimatedDelivery) { 
        this.estimatedDelivery = estimatedDelivery; 
    }

    public String getStatus() { 
        return status; 
    }
    
    public void setStatus(String status) { 
        this.status = status; 
    }

    public String getErrorCode() { 
        return errorCode; 
    }
    
    public void setErrorCode(String errorCode) { 
        this.errorCode = errorCode; 
    }

    public String getErrorMessage() { 
        return errorMessage; 
    }
    
    public void setErrorMessage(String errorMessage) { 
        this.errorMessage = errorMessage; 
    }

    @Override
    public String toString() {
        return "AsyncFlowResponse{" +
                "queued=" + queued +
                ", queueId='" + queueId + '\'' +
                ", estimatedDelivery='" + estimatedDelivery + '\'' +
                ", status='" + status + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
