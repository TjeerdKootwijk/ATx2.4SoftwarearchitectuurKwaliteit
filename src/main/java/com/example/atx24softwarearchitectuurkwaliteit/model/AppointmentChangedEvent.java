package com.example.atx24softwarearchitectuurkwaliteit.model;

import java.time.LocalDateTime;

public class AppointmentChangedEvent {
    private String eventId;  // Unique event ID for idempotency
    private String tenantId; // Multi-tenant support
    private String appointmentId;
    private String appointmentUuid; // OpenMRS UUID
    private String patientId;
    private String patientName;
    private LocalDateTime appointmentDateTime;
    private String status;  // SCHEDULED, RESCHEDULED, CANCELLED, COMPLETED
    private String changeType; // CREATED, UPDATED, DELETED
    private String providerId;
    private String providerName;
    private String location;
    private LocalDateTime eventOccurredAt;  // When the change happened in OpenMRS
    private LocalDateTime receivedAt;       // When we received it
    private String source;  // WEBHOOK or POLLING
    private String timezone = "UTC"; // IANA ID of the tenant's local timezone (NFR13)

    public AppointmentChangedEvent() {}

    public AppointmentChangedEvent(String eventId, String tenantId, String appointmentId) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.appointmentId = appointmentId;
        this.receivedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
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

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = (timezone != null && !timezone.isBlank()) ? timezone : "UTC";
    }

    @Override
    public String toString() {
        return "AppointmentChangedEvent{" +
                "eventId='" + eventId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", appointmentId='" + appointmentId + '\'' +
                ", patientName='" + patientName + '\'' +
                ", appointmentDateTime=" + appointmentDateTime +
                ", status='" + status + '\'' +
                ", source='" + source + '\'' +
                '}';
    }
}
