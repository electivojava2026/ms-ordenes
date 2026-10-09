package com.eventpass.ms_ordenes.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.eventpass.ms_ordenes.model.Orden;

/** Implementacion temporal: no envia nada a ninguna cola, solo deja registro en el log. */
@Component
public class LogOrdenPublisher implements OrdenPublisher {

    private static final Logger log = LoggerFactory.getLogger(LogOrdenPublisher.class);

    @Override
    public void publicar(Orden orden) {
        // TODO: enviar a SQS { ordenId, eventoId, tipoEntradaId, cantidad }
        log.info("Orden {} pendiente de emision (evento={}, tipo={}, cantidad={})",
                orden.getId(), orden.getEventoId(), orden.getTipoEntradaId(), orden.getCantidad());
    }
}
