package com.eventpass.ms_ordenes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.transaction.annotation.Transactional;

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
import com.eventpass.ms_ordenes.model.EstadoOrden;
import com.eventpass.ms_ordenes.service.OrdenService;

@SpringBootTest
@Transactional
class OrdenServiceTest {

    private static final String COMPRADOR = "user@eventpass.com";

    @Autowired
    private OrdenService ordenService;

    @MockitoBean
    private TicketClient ticketClient;

    @MockitoBean
    private EventoClient eventoClient;

    private OrdenResponse crearEjemplo(String email) {
        return ordenService.crear(email, new OrdenRequest(1L, 1L, 2));
    }

    @Test
    void crearOrdenQuedaPendienteDeEmision() {
        OrdenResponse orden = crearEjemplo(COMPRADOR);

        assertNotNull(orden.id());
        assertEquals(EstadoOrden.PENDIENTE_EMISION, orden.estado());
        assertEquals(COMPRADOR, orden.compradorEmail());
        assertEquals(2, orden.cantidad());
        assertNotNull(orden.fechaCreacion());
    }

    @Test
    void rechazaOrdenesInvalidas() {
        assertThrows(SolicitudInvalidaException.class,
                () -> ordenService.crear(COMPRADOR, new OrdenRequest(null, 1L, 1)));
        assertThrows(SolicitudInvalidaException.class,
                () -> ordenService.crear(COMPRADOR, new OrdenRequest(1L, null, 1)));
        assertThrows(SolicitudInvalidaException.class,
                () -> ordenService.crear(COMPRADOR, new OrdenRequest(1L, 1L, 0)));
    }

    @Test
    void compradorSoloVeSusOrdenes() {
        OrdenResponse propia = crearEjemplo(COMPRADOR);
        OrdenResponse ajena = crearEjemplo("otro@eventpass.com");

        when(ticketClient.listarPorOrden(propia.id()))
                .thenReturn(List.of(new TicketResponse(1L, "TKT-TEST0001", 1L, propia.id(), "GENERADO", null)));

        List<CompraResponse> mias = ordenService.misOrdenes(COMPRADOR);
        assertEquals(1, mias.size());
        assertEquals(propia.id(), mias.get(0).orden().id());
        assertEquals("TKT-TEST0001", mias.get(0).tickets().get(0).ticketNumber());

        assertThrows(RecursoNoEncontradoException.class,
                () -> ordenService.obtener(ajena.id(), COMPRADOR, false));
        // el staff si puede ver cualquier orden
        assertEquals(ajena.id(), ordenService.obtener(ajena.id(), "staff@eventpass.com", true).id());
    }

    @Test
    void staffListaTodasYFiltraPorEstado() {
        OrdenResponse a = crearEjemplo(COMPRADOR);
        crearEjemplo("otro@eventpass.com");
        ordenService.actualizarEstado(a.id(), new CambioEstadoRequest(EstadoOrden.EMITIDA, null));

        assertEquals(2, ordenService.listar(null).size());
        assertEquals(1, ordenService.listar(EstadoOrden.EMITIDA).size());
        assertEquals(1, ordenService.listar(EstadoOrden.PENDIENTE_EMISION).size());
    }

    @Test
    void marcaOrdenComoEmitidaORechazadaConMotivo() {
        OrdenResponse emitida = ordenService.actualizarEstado(crearEjemplo(COMPRADOR).id(),
                new CambioEstadoRequest(EstadoOrden.EMITIDA, "ignorado"));
        assertEquals(EstadoOrden.EMITIDA, emitida.estado());
        assertNull(emitida.motivo());

        OrdenResponse rechazada = ordenService.actualizarEstado(crearEjemplo(COMPRADOR).id(),
                new CambioEstadoRequest(EstadoOrden.RECHAZADA, "Sin aforo"));
        assertEquals(EstadoOrden.RECHAZADA, rechazada.estado());
        assertEquals("Sin aforo", rechazada.motivo());
    }

    @Test
    void unaOrdenResueltaNoSePuedeCambiarDeNuevo() {
        OrdenResponse orden = crearEjemplo(COMPRADOR);
        ordenService.actualizarEstado(orden.id(), new CambioEstadoRequest(EstadoOrden.EMITIDA, null));

        assertThrows(EstadoInvalidoException.class,
                () -> ordenService.actualizarEstado(orden.id(), new CambioEstadoRequest(EstadoOrden.RECHAZADA, "x")));
    }

    @Test
    void validaElCambioDeEstado() {
        OrdenResponse orden = crearEjemplo(COMPRADOR);

        assertThrows(SolicitudInvalidaException.class,
                () -> ordenService.actualizarEstado(orden.id(), new CambioEstadoRequest(null, null)));
        assertThrows(SolicitudInvalidaException.class,
                () -> ordenService.actualizarEstado(orden.id(),
                        new CambioEstadoRequest(EstadoOrden.PENDIENTE_EMISION, null)));
        assertThrows(RecursoNoEncontradoException.class,
                () -> ordenService.actualizarEstado(9999L, new CambioEstadoRequest(EstadoOrden.EMITIDA, null)));
    }

    @Test
    void comprarEmiteUnTicketPorUnidad() {
        when(ticketClient.generar(anyLong(), anyLong(), any()))
                .thenReturn(new TicketResponse(1L, "TKT-TEST0001", 1L, 1L, "GENERADO", null));

        CompraResponse compra = ordenService.comprar(COMPRADOR, new OrdenRequest(1L, 1L, 2), "Bearer x");

        assertEquals(EstadoOrden.EMITIDA, compra.orden().estado());
        assertEquals(2, compra.tickets().size());
        verify(eventoClient).reservar(1L, 1L, 2);
    }

    @Test
    void comprarQuedaRechazadaSinAforoYNoEmiteTickets() {
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null))
                .when(eventoClient).reservar(anyLong(), anyLong(), anyInt());

        CompraResponse compra = ordenService.comprar(COMPRADOR, new OrdenRequest(1L, 1L, 1), "Bearer x");

        assertEquals(EstadoOrden.RECHAZADA, compra.orden().estado());
        assertEquals("Sin aforo", compra.orden().motivo());
        assertEquals(0, compra.tickets().size());
        verify(ticketClient, never()).generar(anyLong(), anyLong(), any());
    }

    @Test
    void comprarQuedaRechazadaSiFallaMsTickets() {
        when(ticketClient.generar(anyLong(), anyLong(), any()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        CompraResponse compra = ordenService.comprar(COMPRADOR, new OrdenRequest(1L, 1L, 1), "Bearer x");

        assertEquals(EstadoOrden.RECHAZADA, compra.orden().estado());
        assertNotNull(compra.orden().motivo());
    }
}
