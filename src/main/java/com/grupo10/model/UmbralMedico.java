package com.grupo10.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Representa los umbrales médicos personalizados para un paciente.
 * Corresponde a la tabla tb_umbral_medico.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmbralMedico {

    private Long idUmbral;
    private Long idPaciente;
    private Integer glucosaMax;
    private Integer glucosaMin;
    private Integer sistolicaMax;
    private Integer diastolicaMax;
    private BigDecimal temperaturaMax;
    private Integer sistolicaMin;
    private Integer diastolicaMin;
    private BigDecimal temperaturaMin;
}
