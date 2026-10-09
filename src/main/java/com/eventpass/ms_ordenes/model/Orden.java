package com.eventpass.ms_ordenes.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ordenes")
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Email del comprador (sale del token JWT emitido por ms-auth)
    @Column(name = "comprador_email", nullable = false)
    private String compradorEmail;

    // Referencias a ms-eventos (otra BD, por eso son ids y no relaciones JPA)
    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Column(name = "tipo_entrada_id", nullable = false)
    private Long tipoEntradaId;

    @Column(nullable = false)
    private Integer cantidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoOrden estado;

    // Motivo del rechazo (por ejemplo "sin aforo"); null mientras no se rechace
    private String motivo;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCompradorEmail() { return compradorEmail; }
    public void setCompradorEmail(String compradorEmail) { this.compradorEmail = compradorEmail; }

    public Long getEventoId() { return eventoId; }
    public void setEventoId(Long eventoId) { this.eventoId = eventoId; }

    public Long getTipoEntradaId() { return tipoEntradaId; }
    public void setTipoEntradaId(Long tipoEntradaId) { this.tipoEntradaId = tipoEntradaId; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public EstadoOrden getEstado() { return estado; }
    public void setEstado(EstadoOrden estado) { this.estado = estado; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
