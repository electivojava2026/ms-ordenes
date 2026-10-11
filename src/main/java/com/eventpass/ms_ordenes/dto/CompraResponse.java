package com.eventpass.ms_ordenes.dto;

import java.util.List;

public record CompraResponse(OrdenResponse orden, List<TicketResponse> tickets) {
}
