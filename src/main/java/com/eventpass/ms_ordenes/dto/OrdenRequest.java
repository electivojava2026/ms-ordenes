package com.eventpass.ms_ordenes.dto;

public record OrdenRequest(Long eventoId, Long tipoEntradaId, Integer cantidad) {
}
