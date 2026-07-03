package com.grupo10.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Representa un registro de control de salud de un paciente.
 * Corresponde a la tabla tb_control_salud.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlSalud {
    
    private Long idControl;
    private Long idPaciente;
    private LocalDateTime fechaHora;
    private String notas;
}
