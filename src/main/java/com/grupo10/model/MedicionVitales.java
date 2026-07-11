package com.grupo10.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Representa las mediciones de signos vitales de un paciente.
 * Corresponde a la tabla tb_medicion_vitales.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicionVitales {

    private Long idControl;
    private Integer presionSistolica;
    private Integer presionDiastolica;
    private BigDecimal temperatura;
    private BigDecimal peso;
    private LocalDateTime fechaHora;
}
