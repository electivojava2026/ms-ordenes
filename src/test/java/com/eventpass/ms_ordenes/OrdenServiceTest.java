package com.eventpass.ms_ordenes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.eventpass.ms_ordenes.dto.CambioEstadoRequest;
import com.eventpass.ms_ordenes.dto.OrdenRequest;
import com.eventpass.ms_ordenes.dto.OrdenResponse;
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

        List<OrdenResponse> mias = ordenService.misOrdenes(COMPRADOR);
        assertEquals(1, mias.size());
        assertEquals(propia.id(), mias.get(0).id());

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
}
