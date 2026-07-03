package com.grupo10.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa una enfermedad crónica.
 * Corresponde a la tabla tb_enfermedad.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enfermedad {

    private Long idEnfermedad;
    private String nombreEnfermedad;
    private String descripcion;
}
