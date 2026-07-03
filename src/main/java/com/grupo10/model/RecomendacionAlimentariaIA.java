package com.grupo10.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Representa una recomendación alimentaria generada por la IA.
 * Corresponde a la tabla tb_recomendacion_alimentaria_ia.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecomendacionAlimentariaIA {

    private Long idRecomendacion;
    private Long idPaciente;
    private String titulo;
    private String contenido;
    private String tipoRecomendacion;
    private LocalDateTime fechaGeneracion;
    private Boolean estadoNotificacion;
    private Boolean leida;
}
