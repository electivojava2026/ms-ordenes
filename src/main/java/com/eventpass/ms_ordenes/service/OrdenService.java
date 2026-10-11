package com.eventpass.ms_ordenes.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import com.eventpass.ms_ordenes.client.EventoClient;
import com.eventpass.ms_ordenes.client.TicketClient;
import com.eventpass.ms_ordenes.dto.CambioEstadoRequest;
import com.eventpass.ms_ordenes.dto.CompraResponse;
import com.eventpass.ms_ordenes.dto.OrdenRequest;
import com.eventpass.ms_ordenes.dto.OrdenResponse;
import com.eventpass.ms_ordenes.dto.TicketResponse;
import com.eventpass.ms_ordenes.exception.EstadoInvalidoException;
import com.eventpass.ms_ordenes.exception.RecursoNoEncontradoException;
import com.eventpass.ms_ordenes.exception.SolicitudInvalidaException;
import com.eventpass.ms_ordenes.messaging.OrdenPublisher;
import com.eventpass.ms_ordenes.model.EstadoOrden;
import com.eventpass.ms_ordenes.model.Orden;
import com.eventpass.ms_ordenes.repository.OrdenRepository;

@Service
@Transactional
public class OrdenService {

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private OrdenPublisher ordenPublisher;

    @Autowired
    private TicketClient ticketClient;

    @Autowired
    private EventoClient eventoClient;

    /**
     * Registra la compra de inmediato como PENDIENTE_EMISION. No consulta ni descuenta el aforo:
     * eso lo hace despues el proceso asincrono (SQS + Lambda) llamando a ms-eventos.
     */
    public OrdenResponse crear(String compradorEmail, OrdenRequest request) {
        validar(request);

        Orden orden = new Orden();
        orden.setCompradorEmail(compradorEmail);
        orden.setEventoId(request.eventoId());
        orden.setTipoEntradaId(request.tipoEntradaId());
        orden.setCantidad(request.cantidad());
        orden.setEstado(EstadoOrden.PENDIENTE_EMISION);
        orden.setFechaCreacion(LocalDateTime.now());
        Orden guardada = ordenRepository.save(orden);

        // Deja la orden en la cola de emision (por ahora solo log)
        ordenPublisher.publicar(guardada);
        return aRespuesta(guardada);
    }

    /**
     * Compra simulada sincrona: crea la orden, descuenta el aforo en ms-eventos y pide a ms-tickets
     * un ticket por unidad. Si no hay aforo o algun servicio falla, la orden queda RECHAZADA con el motivo.
     */
    public CompraResponse comprar(String compradorEmail, OrdenRequest request, String authorization) {
        OrdenResponse orden = crear(compradorEmail, request);
        List<TicketResponse> tickets = new ArrayList<>();
        try {
            eventoClient.reservar(orden.eventoId(), orden.tipoEntradaId(), orden.cantidad());
            for (int i = 0; i < orden.cantidad(); i++) {
                tickets.add(ticketClient.generar(orden.eventoId(), orden.id(), authorization));
            }
            orden = actualizarEstado(orden.id(), new CambioEstadoRequest(EstadoOrden.EMITIDA, null));
        } catch (HttpClientErrorException.Conflict e) {
            orden = actualizarEstado(orden.id(), new CambioEstadoRequest(EstadoOrden.RECHAZADA, "Sin aforo"));
        } catch (RestClientException e) {
            orden = actualizarEstado(orden.id(), new CambioEstadoRequest(EstadoOrden.RECHAZADA,
                    "No se pudo emitir el ticket: " + e.getMessage()));
        }
        return new CompraResponse(orden, tickets);
    }

    // Comprador: sus propias ordenes, cada una con sus tickets (consultados a ms-tickets)
    @Transactional(readOnly = true)
    public List<CompraResponse> misOrdenes(String compradorEmail) {
        return ordenRepository.findByCompradorEmailOrderByFechaCreacionDesc(compradorEmail).stream()
                .map(o -> new CompraResponse(aRespuesta(o), ticketClient.listarPorOrden(o.getId())))
                .toList();
    }

    // Staff: todas las ordenes, con filtro opcional por estado
    @Transactional(readOnly = true)
    public List<OrdenResponse> listar(EstadoOrden estado) {
        List<Orden> ordenes = estado == null
                ? ordenRepository.findAllByOrderByFechaCreacionDesc()
                : ordenRepository.findByEstadoOrderByFechaCreacionDesc(estado);
        return ordenes.stream().map(this::aRespuesta).toList();
    }

    // El comprador solo ve las suyas (si no es suya responde 404); el staff ve cualquiera
    @Transactional(readOnly = true)
    public OrdenResponse obtener(Long id, String email, boolean esStaff) {
        Orden orden = buscar(id);
        if (!esStaff && !orden.getCompradorEmail().equals(email)) {
            throw new RecursoNoEncontradoException("Orden con id " + id + " no encontrada");
        }
        return aRespuesta(orden);
    }

    /**
     * Lo usara el proceso de emision: PENDIENTE_EMISION -> EMITIDA o RECHAZADA.
     * Una orden que ya fue resuelta no se puede volver a cambiar (409).
     */
    public OrdenResponse actualizarEstado(Long id, CambioEstadoRequest request) {
        if (request == null || request.estado() == null) {
            throw new SolicitudInvalidaException("El estado es obligatorio");
        }
        if (request.estado() == EstadoOrden.PENDIENTE_EMISION) {
            throw new SolicitudInvalidaException("El nuevo estado debe ser EMITIDA o RECHAZADA");
        }

        Orden orden = buscar(id);
        if (orden.getEstado() != EstadoOrden.PENDIENTE_EMISION) {
            throw new EstadoInvalidoException("La orden " + id + " ya esta en estado " + orden.getEstado());
        }

        orden.setEstado(request.estado());
        orden.setMotivo(request.estado() == EstadoOrden.RECHAZADA ? request.motivo() : null);
        return aRespuesta(ordenRepository.save(orden));
    }

    // ---------- Auxiliares ----------

    private Orden buscar(Long id) {
        return ordenRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Orden con id " + id + " no encontrada"));
    }

    private void validar(OrdenRequest r) {
        if (r == null || r.eventoId() == null) {
            throw new SolicitudInvalidaException("El evento es obligatorio");
        }
        if (r.tipoEntradaId() == null) {
            throw new SolicitudInvalidaException("El tipo de entrada es obligatorio");
        }
        if (r.cantidad() == null || r.cantidad() <= 0) {
            throw new SolicitudInvalidaException("La cantidad debe ser mayor a 0");
        }
    }

    private OrdenResponse aRespuesta(Orden o) {
        return new OrdenResponse(o.getId(), o.getCompradorEmail(), o.getEventoId(), o.getTipoEntradaId(),
                o.getCantidad(), o.getEstado(), o.getMotivo(), o.getFechaCreacion());
    }
}
