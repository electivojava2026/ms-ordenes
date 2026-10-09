package com.eventpass.ms_ordenes.model;

/**
 * Ciclo de vida de una orden:
 * PENDIENTE_EMISION -> EMITIDA (ticket generado) o RECHAZADA (sin aforo).
 */
public enum EstadoOrden {
    PENDIENTE_EMISION, EMITIDA, RECHAZADA
}
