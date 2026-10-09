package com.eventpass.ms_ordenes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.eventpass.ms_ordenes.security.JwtService;

/** Verifica que cada rol (USER / STAFF) acceda solo a los endpoints que le corresponden. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrdenSeguridadTest {

    private static final String BODY = "{\"eventoId\":1,\"tipoEntradaId\":1,\"cantidad\":2}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String bearer(String role) {
        return "Bearer " + jwtService.generateToken(role.toLowerCase() + "@eventpass.com", role);
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/ordenes/mis-ordenes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalidoDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/ordenes/mis-ordenes").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void compradorPuedeCrearOrdenYQuedaPendiente() throws Exception {
        mockMvc.perform(post("/api/v1/ordenes")
                .header("Authorization", bearer("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_EMISION"))
                .andExpect(jsonPath("$.compradorEmail").value("user@eventpass.com"));
    }

    @Test
    void staffNoPuedeCrearOrdenes() throws Exception {
        mockMvc.perform(post("/api/v1/ordenes")
                .header("Authorization", bearer("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void compradorPuedeVerSusOrdenes() throws Exception {
        mockMvc.perform(get("/api/v1/ordenes/mis-ordenes").header("Authorization", bearer("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void compradorNoPuedeListarTodasLasOrdenes() throws Exception {
        mockMvc.perform(get("/api/v1/ordenes").header("Authorization", bearer("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffPuedeListarTodasLasOrdenes() throws Exception {
        mockMvc.perform(get("/api/v1/ordenes").header("Authorization", bearer("STAFF")))
                .andExpect(status().isOk());
    }

    @Test
    void compradorNoPuedeCambiarElEstado() throws Exception {
        mockMvc.perform(patch("/api/v1/ordenes/1/estado")
                .header("Authorization", bearer("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EMITIDA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCambiaEstadoDeUnaOrdenInexistenteDevuelve404() throws Exception {
        mockMvc.perform(patch("/api/v1/ordenes/9999/estado")
                .header("Authorization", bearer("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EMITIDA\"}"))
                .andExpect(status().isNotFound());
    }
}
