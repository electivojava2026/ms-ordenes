package com.eventpass.ms_ordenes.dto;

import com.eventpass.ms_ordenes.model.EstadoOrden;

public record CambioEstadoRequest(EstadoOrden estado, String motivo) {
}
