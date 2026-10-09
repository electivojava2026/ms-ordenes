package com.eventpass.ms_ordenes.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eventpass.ms_ordenes.dto.CambioEstadoRequest;
import com.eventpass.ms_ordenes.dto.OrdenRequest;
import com.eventpass.ms_ordenes.dto.OrdenResponse;
import com.eventpass.ms_ordenes.model.EstadoOrden;
import com.eventpass.ms_ordenes.service.OrdenService;

@RestController
@RequestMapping("/api/v1/ordenes")
public class OrdenController {

    @Autowired
    private OrdenService ordenService;

    // --- Comprador (USER) ---

    // Registra la compra: queda PENDIENTE_EMISION (el ticket se emite despues, de forma asincrona)
    @PostMapping
    public ResponseEntity<OrdenResponse> crear(Authentication auth, @RequestBody OrdenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ordenService.crear(auth.getName(), request));
    }

    @GetMapping("/mis-ordenes")
    public ResponseEntity<List<OrdenResponse>> misOrdenes(Authentication auth) {
        return ResponseEntity.ok(ordenService.misOrdenes(auth.getName()));
    }

    // USER (solo las suyas) y STAFF (cualquiera)
    @GetMapping("/{id}")
    public ResponseEntity<OrdenResponse> obtener(Authentication auth, @PathVariable Long id) {
        boolean esStaff = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
        return ResponseEntity.ok(ordenService.obtener(id, auth.getName(), esStaff));
    }

    // --- Solo STAFF (ver SecurityConfig) ---

    @GetMapping
    public ResponseEntity<List<OrdenResponse>> listar(@RequestParam(required = false) EstadoOrden estado) {
        return ResponseEntity.ok(ordenService.listar(estado));
    }

    // Lo usara el proceso de emision para marcar la orden como EMITIDA o RECHAZADA
    @PatchMapping("/{id}/estado")
    public ResponseEntity<OrdenResponse> actualizarEstado(@PathVariable Long id,
            @RequestBody CambioEstadoRequest request) {
        return ResponseEntity.ok(ordenService.actualizarEstado(id, request));
    }
}
