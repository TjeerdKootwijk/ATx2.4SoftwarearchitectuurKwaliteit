package com.example.atx24softwarearchitectuurkwaliteit.dto;

import java.time.LocalDateTime;

/**
 * DTO for webhook payload from OpenMRS
 */
public class AppointmentEventDto {
    private String eventId;
    private String appointmentId;
    private String appointmentUuid;
    private String patientId;
    private String patientName;
    private LocalDateTime appointmentDateTime;
    private String status;
    private String changeType;
    private String providerId;
    private String providerName;
    private String location;
    private LocalDateTime eventOccurredAt;

    public AppointmentEventDto() {}

    // Getters and Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(String appointmentId) {
        this.appointmentId = appointmentId;
    }

    public String getAppointmentUuid() {
        return appointmentUuid;
    }

    public void setAppointmentUuid(String appointmentUuid) {
        this.appointmentUuid = appointmentUuid;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public LocalDateTime getAppointmentDateTime() {
        return appointmentDateTime;
    }

    public void setAppointmentDateTime(LocalDateTime appointmentDateTime) {
        this.appointmentDateTime = appointmentDateTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDateTime getEventOccurredAt() {
        return eventOccurredAt;
    }

    public void setEventOccurredAt(LocalDateTime eventOccurredAt) {
        this.eventOccurredAt = eventOccurredAt;
    }
}
