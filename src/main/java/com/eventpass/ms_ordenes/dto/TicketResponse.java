package com.eventpass.ms_ordenes.dto;

import java.time.LocalDateTime;

// Ticket tal como lo devuelve ms-tickets
public record TicketResponse(Long id, String ticketNumber, Long eventId, Long orderId,
        String status, LocalDateTime createdAt) {
}
