package com.grupo10.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa una medición de perfil lipídico de un paciente.
 * Corresponde a la tabla tb_medicion_lipidos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicionLipidos {

    private Long idControl;
    private Integer colesterolTotal;
    private Integer colesterolLdl;
    private Integer colesterolHdl;
    private Integer trigliceridos;
}
