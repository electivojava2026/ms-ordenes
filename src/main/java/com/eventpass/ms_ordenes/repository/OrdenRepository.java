package com.eventpass.ms_ordenes.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eventpass.ms_ordenes.model.EstadoOrden;
import com.eventpass.ms_ordenes.model.Orden;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, Long> {

    List<Orden> findByCompradorEmailOrderByFechaCreacionDesc(String compradorEmail);

    List<Orden> findByEstadoOrderByFechaCreacionDesc(EstadoOrden estado);

    List<Orden> findAllByOrderByFechaCreacionDesc();
}
