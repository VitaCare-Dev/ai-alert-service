package com.grupo10.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Representa una alerta generada por la IA para un paciente.
 * Corresponde a la tabla tb_alerta_ia.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertaIA {

    private Long idAlertaIa;
    private Long idPaciente;
    private LocalDateTime fechaDisparo;
    private String motivoAlerta;
    private String recomendacionIa;
    private Boolean leida;
}
