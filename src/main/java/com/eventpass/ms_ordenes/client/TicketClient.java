package com.eventpass.ms_ordenes.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.eventpass.ms_ordenes.dto.TicketResponse;
import com.eventpass.ms_ordenes.security.JwtService;

/**
 * Llama a ms-tickets. Generar reenvia el token del comprador; listar por orden usa la cuenta de
 * servicio (ms-tickets no sabe de quien es cada orden, la propiedad la valida ms-ordenes).
 */
@Component
public class TicketClient {

    @Value("${tickets.url:http://localhost:8084}")
    private String ticketsUrl;

    @Autowired
    private JwtService jwtService;

    public TicketResponse generar(Long eventoId, Long ordenId, String authorization) {
        return RestClient.create(ticketsUrl).post()
                .uri("/api/v1/tickets/generate?eventId={e}&orderId={o}", eventoId, ordenId)
                .header("Authorization", authorization)
                .retrieve()
                .body(TicketResponse.class);
    }

    public List<TicketResponse> listarPorOrden(Long ordenId) {
        return RestClient.create(ticketsUrl).get()
                .uri("/api/v1/tickets/order/{o}", ordenId)
                .header("Authorization", "Bearer " + jwtService.generateServiceToken())
                .retrieve()
                .body(new ParameterizedTypeReference<List<TicketResponse>>() {});
    }
}
