package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;

/**
 * SRP: verantwoordelijk voor het publiceren van een AppointmentChangedEvent naar de queue.
 * DIP: PollingJob hangt af van deze abstractie, niet van RabbitMQ direct.
 */
public interface AppointmentEventPublisher {
    void publish(AppointmentChangedEvent event);
}
