package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class IdempotencyService {

    private static final Logger log =
            LoggerFactory.getLogger(IdempotencyService.class);

    private final AppointmentService appointmentService;

    public IdempotencyService(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * Checks whether the appointment was already processed.
     *
     * If the appointment is NOT a duplicate,
     * it forwards the event to AppointmentService.
     */
    public void processAppointment(AppointmentChangedEvent event) {

        String tenantId = event.getTenantId();
        String appointmentId = event.getAppointmentId();
        String changeType = event.getChangeType();

        log.debug("Checking idempotency for appointment {}",
                appointmentId);

        // FUTURE DATABASE CALL:
        // boolean exists = databaseService.existsAppointmentEvent(
        //         tenantId,
        //         appointmentId,
        //         changeType
        // );

        // Temporary placeholder
        boolean exists = false;

        if (exists) {
            log.info("Duplicate appointment skipped: {}", appointmentId);
            return;
        }

        log.info("New appointment detected: {}", appointmentId);

        // Forward to next service
        appointmentService.handleAppointment(event);
    }

}
