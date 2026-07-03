package com.grupo10.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa la relación entre un paciente y sus enfermedades crónicas.
 * Corresponde a la tabla tb_paciente_enfermedad.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacienteEnfermedad {

    private Long idPaciente;
    private Long idEnfermedad;
}
