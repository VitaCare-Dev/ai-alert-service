package com.grupo10.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa una medición de glucosa de un paciente.
 * Corresponde a la tabla tb_medicion_glucosa.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicionGlucosa {

    private Long idControl;
    private Integer glucosa;
    private String periodo;
}
