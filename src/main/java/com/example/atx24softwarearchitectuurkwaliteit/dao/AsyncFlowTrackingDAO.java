package com.example.atx24softwarearchitectuurkwaliteit.dao;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.AsyncFlowTrackingEntity;

import java.util.List;

/**
 * Data access contract voor het bijhouden van nog niet afgeronde AsyncFlow-berichten.
 *
 * AsyncFlow is asynchroon; deze records vormen de werklijst voor de
 * {@code AsyncFlowStatusPoller} die de definitieve afleverstatus ophaalt.
 */
public interface AsyncFlowTrackingDAO {

    /** Slaat een nieuw (PENDING) tracking-record op. */
    AsyncFlowTrackingEntity save(AsyncFlowTrackingEntity entity);

    /** Haalt maximaal {@code limit} records met de gegeven status op, oudste eerst. */
    List<AsyncFlowTrackingEntity> findByStatus(String status, int limit);

    /** Verwijdert een tracking-record (zodra de status definitief is afgehandeld). */
    void delete(AsyncFlowTrackingEntity entity);
}
