package com.eventpass.ms_ordenes.dto;

import java.time.LocalDateTime;

import com.eventpass.ms_ordenes.model.EstadoOrden;

public record OrdenResponse(Long id, String compradorEmail, Long eventoId, Long tipoEntradaId,
        Integer cantidad, EstadoOrden estado, String motivo, LocalDateTime fechaCreacion) {
}
