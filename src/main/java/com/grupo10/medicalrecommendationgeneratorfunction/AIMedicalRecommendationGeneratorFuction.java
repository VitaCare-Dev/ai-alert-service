package com.grupo10.medicalrecommendationgeneratorfunction;

import com.grupo10.model.*;
import com.grupo10.service.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Azure Function que genera recomendaciones alimentarias personalizadas
 * basadas en las enfermedades crónicas de cada paciente.
 * 
 * Se ejecuta cada 3 días mediante un Timer Trigger.
 */
public class AIMedicalRecommendationGeneratorFuction {

    private static final Logger logger = Logger.getLogger(AIMedicalRecommendationGeneratorFuction.class.getName());

    /**
     * Timer Trigger: Se ejecuta cada 3 dias para generar recomendaciones
     * alimentarias.
     * CRON: 0 0 0 asterisk-slash-3 asterisk asterisk (cada 3 dias a medianoche)
     */
    @FunctionName("AIMedicalRecommendationGenerator")
    public void run(
            @TimerTrigger(name = "timerInfo", schedule = "0 0 0 */3 * *") String timerInfo,
            final ExecutionContext context) {
        logger.info("=== Iniciando generación de recomendaciones alimentarias ===");
        logger.info("Timestamp: " + LocalDateTime.now());

        try {
            // Obtener todos los pacientes con enfermedades registradas
            List<Long> pacientes = EnfermedadService.obtenerPacientesConEnfermedades();

            if (pacientes.isEmpty()) {
                logger.info("No se encontraron pacientes con enfermedades registradas.");
                return;
            }

            logger.info("Generando recomendaciones para " + pacientes.size() + " pacientes...");

            int recomendacionesGeneradas = 0;

            for (Long idPaciente : pacientes) {
                try {
                    boolean recomendacionGenerada = generarRecomendacionPaciente(idPaciente);
                    if (recomendacionGenerada) {
                        recomendacionesGeneradas++;
                    }
                } catch (Exception e) {
                    logger.severe(
                            "Error al generar recomendación para paciente ID " + idPaciente + ": " + e.getMessage());
                }
            }

            logger.info("=== Generación completada. Recomendaciones generadas: " + recomendacionesGeneradas + " ===");

        } catch (Exception e) {
            logger.severe("Error en el proceso de generación de recomendaciones: " + e.getMessage());
            throw new RuntimeException("Error en AIMedicalRecommendationGenerator", e);
        }
    }

    /**
     * HTTP Trigger alternativo para generación manual de recomendación.
     */
    @FunctionName("AIMedicalRecommendationGeneratorManual")
    public HttpResponseMessage runManual(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.POST }, authLevel = AuthorizationLevel.FUNCTION, route = "generar-recomendacion/{idPaciente}") HttpRequestMessage<String> request,
            @BindingName("idPaciente") Long idPaciente,
            final ExecutionContext context) {
        logger.info("Generación manual solicitada para paciente ID: " + idPaciente);

        try {
            boolean recomendacionGenerada = generarRecomendacionPaciente(idPaciente);

            if (recomendacionGenerada) {
                return request.createResponseBuilder(HttpStatus.OK)
                        .body("Recomendación generada exitosamente para el paciente ID: " + idPaciente)
                        .build();
            } else {
                return request.createResponseBuilder(HttpStatus.OK)
                        .body("No fue necesario generar una nueva recomendación para el paciente ID: " + idPaciente)
                        .build();
            }

        } catch (Exception e) {
            logger.severe("Error en generación manual: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al generar recomendación: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Genera una recomendación alimentaria personalizada para un paciente.
     * 
     * @param idPaciente ID del paciente
     * @return true si se generó una recomendación, false en caso contrario
     */
    private boolean generarRecomendacionPaciente(Long idPaciente) {
        logger.info("Generando recomendación para paciente ID: " + idPaciente);

        // Verificar si ya existe una recomendación reciente (últimos 2 días)
        if (RecomendacionService.existeRecomendacionReciente(idPaciente, 2)) {
            logger.info("Ya existe una recomendación reciente para el paciente ID " + idPaciente);
            return false;
        }

        // Obtener las enfermedades del paciente
        List<Enfermedad> enfermedades = EnfermedadService.obtenerEnfermedadesPorPaciente(idPaciente);

        if (enfermedades.isEmpty()) {
            logger.warning("Paciente ID " + idPaciente + " no tiene enfermedades registradas.");
            return false;
        }

        // Construir lista de nombres de enfermedades
        String listaEnfermedades = enfermedades.stream()
                .map(Enfermedad::getNombreEnfermedad)
                .collect(Collectors.joining(", "));

        logger.info("Enfermedades del paciente ID " + idPaciente + ": " + listaEnfermedades);

        try {
            // Generar recomendación usando IA
            String recomendacionIA = generarRecomendacionConIA(enfermedades);

            // Generar título basado en la enfermedad principal
            String titulo = generarTituloRecomendacion(enfermedades);

            // Crear y guardar la recomendación
            RecomendacionAlimentariaIA recomendacion = RecomendacionAlimentariaIA.builder()
                    .idPaciente(idPaciente)
                    .titulo(titulo)
                    .contenido(recomendacionIA)
                    .tipoRecomendacion("ALIMENTARIA")
                    .fechaGeneracion(LocalDateTime.now())
                    .estadoNotificacion(false)
                    .leida(false)
                    .build();

            Long idRecomendacion = RecomendacionService.guardarRecomendacion(recomendacion);

            logger.info("✓ Recomendación generada exitosamente con ID: " + idRecomendacion + " para paciente ID: "
                    + idPaciente);
            return true;

        } catch (Exception e) {
            logger.severe("Error al generar recomendación para paciente ID " + idPaciente + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Genera recomendaciones alimentarias usando IA basadas en las enfermedades.
     */
    private String generarRecomendacionConIA(List<Enfermedad> enfermedades) throws Exception {
        // Construir el prompt específico según las enfermedades
        StringBuilder prompt = new StringBuilder();
        prompt.append("Genera recomendaciones alimentarias breves (máximo 200 palabras) para un paciente con: ");

        for (int i = 0; i < enfermedades.size(); i++) {
            Enfermedad enfermedad = enfermedades.get(i);
            prompt.append(enfermedad.getNombreEnfermedad());

            if (i < enfermedades.size() - 1) {
                prompt.append(", ");
            }
        }

        prompt.append(". ");

        // Añadir recomendaciones específicas según el tipo de enfermedad
        for (Enfermedad enfermedad : enfermedades) {
            String nombreEnfermedad = enfermedad.getNombreEnfermedad().toLowerCase();

            if (nombreEnfermedad.contains("diabetes")) {
                prompt.append(
                        "Para diabetes: reducir carbohidratos simples, aumentar fibra, evitar azúcares refinados. ");
            } else if (nombreEnfermedad.contains("hipertens") || nombreEnfermedad.contains("presión")) {
                prompt.append(
                        "Para hipertensión: reducir sodio, aumentar potasio (frutas y verduras), evitar alimentos procesados. ");
            } else if (nombreEnfermedad.contains("colesterol") || nombreEnfermedad.contains("dislipidemia")) {
                prompt.append("Para colesterol alto: reducir grasas saturadas, aumentar omega-3, consumir más fibra. ");
            } else if (nombreEnfermedad.contains("obesidad")) {
                prompt.append("Para obesidad: controlar porciones, aumentar vegetales, reducir calorías vacías. ");
            } else if (nombreEnfermedad.contains("renal") || nombreEnfermedad.contains("riñón")) {
                prompt.append(
                        "Para problemas renales: controlar proteínas, reducir fósforo y potasio, limitar sodio. ");
            }
        }

        prompt.append("Incluye alimentos recomendados, alimentos a evitar y consejos prácticos de hidratación. ");
        prompt.append("Usa un tono motivador y cercano.");

        logger.info("Consultando IA para generar recomendación alimentaria...");
        return GroqService.consultarGroq(prompt.toString());
    }

    /**
     * Genera un título descriptivo para la recomendación.
     */
    private String generarTituloRecomendacion(List<Enfermedad> enfermedades) {
        if (enfermedades.size() == 1) {
            String nombre = enfermedades.get(0).getNombreEnfermedad();
            return "Recomendaciones para " + nombre;
        } else if (enfermedades.size() == 2) {
            return "Recomendaciones para " +
                    enfermedades.get(0).getNombreEnfermedad() + " y " +
                    enfermedades.get(1).getNombreEnfermedad();
        } else {
            return "Recomendaciones Alimentarias Personalizadas";
        }
    }
}
