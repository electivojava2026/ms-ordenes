package com.eventpass.ms_ordenes.client;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.eventpass.ms_ordenes.security.JwtService;

/**
 * Llama a ms-eventos para descontar aforo. Reservar es solo para STAFF/SERVICE, asi que usa
 * un token de cuenta de servicio firmado por ms-ordenes (no el token del comprador).
 */
@Component
public class EventoClient {

    @Value("${eventos.url:http://localhost:8082}")
    private String eventosUrl;

    @Autowired
    private JwtService jwtService;

    // Lanza HttpClientErrorException.Conflict (409) si no queda aforo
    public void reservar(Long eventoId, Long tipoEntradaId, Integer cantidad) {
        RestClient.create(eventosUrl).post()
                .uri("/api/v1/eventos/{e}/tipos-entrada/{t}/reservas", eventoId, tipoEntradaId)
                .header("Authorization", "Bearer " + jwtService.generateServiceToken())
                .body(Map.of("cantidad", cantidad))
                .retrieve()
                .toBodilessEntity();
    }
}
