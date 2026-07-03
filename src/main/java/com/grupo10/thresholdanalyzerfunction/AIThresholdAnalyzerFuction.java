package com.grupo10.thresholdanalyzerfunction;

import com.grupo10.model.*;
import com.grupo10.service.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;

/**
 * Azure Function que analiza mediciones de salud y genera alertas cuando
 * se detectan valores alterados por 2-3 días consecutivos.
 * 
 * Se ejecuta cada 6 horas mediante un Timer Trigger.
 */
public class AIThresholdAnalyzerFuction {

    private static final Logger logger = Logger.getLogger(AIThresholdAnalyzerFuction.class.getName());

    /**
     * Timer Trigger: Se ejecuta cada 6 horas para analizar mediciones.
     * CRON: 0 0 asterisk-slash-6 asterisk asterisk asterisk (cada 6 horas en el
     * minuto 0)
     */
    @FunctionName("AIThresholdAnalyzer")
    public void run(
            @TimerTrigger(name = "timerInfo", schedule = "0 0 */6 * * *") String timerInfo,
            final ExecutionContext context) {
        logger.info("=== Iniciando análisis de umbrales de mediciones ===");
        logger.info("Timestamp: " + LocalDateTime.now());

        try {
            // Obtener todos los pacientes con mediciones recientes
            List<Long> pacientes = MedicionService.obtenerPacientesConMediciones();

            if (pacientes.isEmpty()) {
                logger.info("No se encontraron pacientes con mediciones recientes.");
                return;
            }

            logger.info("Analizando " + pacientes.size() + " pacientes...");

            int alertasGeneradas = 0;

            for (Long idPaciente : pacientes) {
                try {
                    boolean alertaGenerada = analizarPaciente(idPaciente);
                    if (alertaGenerada) {
                        alertasGeneradas++;
                    }
                } catch (Exception e) {
                    logger.severe("Error al analizar paciente ID " + idPaciente + ": " + e.getMessage());
                }
            }

            logger.info("=== Análisis completado. Alertas generadas: " + alertasGeneradas + " ===");

        } catch (Exception e) {
            logger.severe("Error en el proceso de análisis de umbrales: " + e.getMessage());
            throw new RuntimeException("Error en AIThresholdAnalyzer", e);
        }
    }

    /**
     * HTTP Trigger alternativo para análisis manual de un paciente específico.
     */
    @FunctionName("AIThresholdAnalyzerManual")
    public HttpResponseMessage runManual(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.POST }, authLevel = AuthorizationLevel.FUNCTION, route = "analizar-umbral/{idPaciente}") HttpRequestMessage<String> request,
            @BindingName("idPaciente") Long idPaciente,
            final ExecutionContext context) {
        logger.info("Análisis manual solicitado para paciente ID: " + idPaciente);

        try {
            boolean alertaGenerada = analizarPaciente(idPaciente);

            if (alertaGenerada) {
                return request.createResponseBuilder(HttpStatus.OK)
                        .body("Alerta generada exitosamente para el paciente ID: " + idPaciente)
                        .build();
            } else {
                return request.createResponseBuilder(HttpStatus.OK)
                        .body("No se detectaron anomalías en las mediciones del paciente ID: " + idPaciente)
                        .build();
            }

        } catch (Exception e) {
            logger.severe("Error en análisis manual: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al analizar paciente: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Analiza las mediciones de un paciente y genera alertas si es necesario.
     * 
     * @param idPaciente ID del paciente a analizar
     * @return true si se generó una alerta, false en caso contrario
     */
    private boolean analizarPaciente(Long idPaciente) {
        logger.info("Analizando paciente ID: " + idPaciente);

        // Obtener umbrales configurados del paciente
        UmbralMedico umbral = UmbralService.obtenerUmbralesPorPaciente(idPaciente);

        if (umbral == null) {
            logger.warning("Paciente ID " + idPaciente + " no tiene umbrales configurados. Omitiendo análisis.");
            return false;
        }

        // Verificar si hay mediciones en días consecutivos
        List<LocalDateTime> fechasMediciones = MedicionService.verificarMedicionesConsecutivas(idPaciente, 3);

        if (fechasMediciones.size() < 2) {
            logger.info("Paciente ID " + idPaciente + " no tiene suficientes mediciones consecutivas.");
            return false;
        }

        // Verificar si las fechas son consecutivas
        int diasConsecutivos = contarDiasConsecutivos(fechasMediciones);

        if (diasConsecutivos < 2) {
            logger.info("Paciente ID " + idPaciente + " no tiene mediciones en días consecutivos.");
            return false;
        }

        // Analizar mediciones de glucosa
        List<MedicionGlucosa> medicionesGlucosa = MedicionService.obtenerMedicionesGlucosaRecientes(idPaciente, 3);
        int glucosaAlterada = contarMedicionesGlucosaAlteradas(medicionesGlucosa, umbral);

        // Analizar mediciones de signos vitales
        List<MedicionVitales> medicionesVitales = MedicionService.obtenerMedicionesVitalesRecientes(idPaciente, 3);
        int vitalesAlterados = contarMedicionesVitalesAlteradas(medicionesVitales, umbral);

        // Si hay mediciones alteradas por 2 o más días, generar alerta
        if (glucosaAlterada >= 2 || vitalesAlterados >= 2) {
            // Evitar alertas duplicadas recientes
            if (AlertaService.existeAlertaReciente(idPaciente, 24)) {
                logger.info("Ya existe una alerta reciente para el paciente ID " + idPaciente);
                return false;
            }

            return generarAlerta(idPaciente, diasConsecutivos, glucosaAlterada, vitalesAlterados, umbral);
        }

        logger.info("No se detectaron anomalías significativas para el paciente ID: " + idPaciente);
        return false;
    }

    /**
     * Cuenta cuántos días consecutivos hay en una lista de fechas.
     */
    private int contarDiasConsecutivos(List<LocalDateTime> fechas) {
        if (fechas.size() < 2) {
            return fechas.size();
        }

        int maxConsecutivos = 1;
        int consecutivosActuales = 1;

        for (int i = 1; i < fechas.size(); i++) {
            long diferenciaDias = ChronoUnit.DAYS.between(
                    fechas.get(i).toLocalDate(),
                    fechas.get(i - 1).toLocalDate());

            if (Math.abs(diferenciaDias) == 1) {
                consecutivosActuales++;
                maxConsecutivos = Math.max(maxConsecutivos, consecutivosActuales);
            } else {
                consecutivosActuales = 1;
            }
        }

        return maxConsecutivos;
    }

    /**
     * Cuenta cuántas mediciones de glucosa están fuera del umbral.
     */
    private int contarMedicionesGlucosaAlteradas(List<MedicionGlucosa> mediciones, UmbralMedico umbral) {
        int contador = 0;

        for (MedicionGlucosa medicion : mediciones) {
            if (medicion.getGlucosa() == null) {
                continue;
            }
            boolean sobreMaximo = umbral.getGlucosaMax() != null && medicion.getGlucosa() > umbral.getGlucosaMax();
            boolean bajoMinimo = umbral.getGlucosaMin() != null && medicion.getGlucosa() < umbral.getGlucosaMin();
            if (sobreMaximo || bajoMinimo) {
                contador++;
            }
        }

        return contador;
    }

    /**
     * Cuenta cuántas mediciones de signos vitales están fuera del umbral.
     */
    private int contarMedicionesVitalesAlteradas(List<MedicionVitales> mediciones, UmbralMedico umbral) {
        int contador = 0;

        for (MedicionVitales medicion : mediciones) {
            boolean alterado = false;

            if (medicion.getPresionSistolica() != null && umbral.getSistolicaMax() != null &&
                    medicion.getPresionSistolica() > umbral.getSistolicaMax()) {
                alterado = true;
            }

            if (medicion.getPresionDiastolica() != null && umbral.getDiastolicaMax() != null &&
                    medicion.getPresionDiastolica() > umbral.getDiastolicaMax()) {
                alterado = true;
            }

            if (medicion.getTemperatura() != null && umbral.getTemperaturaMax() != null &&
                    medicion.getTemperatura().compareTo(umbral.getTemperaturaMax()) > 0) {
                alterado = true;
            }

            if (alterado) {
                contador++;
            }
        }

        return contador;
    }

    /**
     * Genera una alerta usando IA y la guarda en la base de datos.
     */
    private boolean generarAlerta(Long idPaciente, int diasConsecutivos,
            int glucosaAlterada, int vitalesAlterados,
            UmbralMedico umbral) {
        try {
            // Construir el prompt para la IA
            StringBuilder prompt = new StringBuilder();
            prompt.append("Genera una alerta médica corta (máximo 150 caracteres) para un paciente. ");
            prompt.append("Se detectaron mediciones alteradas por ").append(diasConsecutivos)
                    .append(" días consecutivos. ");

            if (glucosaAlterada > 0) {
                prompt.append("Glucosa fuera del rango (").append(umbral.getGlucosaMin())
                        .append("-").append(umbral.getGlucosaMax()).append(" mg/dL). ");
            }

            if (vitalesAlterados > 0) {
                prompt.append("Signos vitales alterados (presión/temperatura). ");
            }

            prompt.append("Recomienda consultar médico para exámenes específicos.");

            logger.info("Consultando IA para generar alerta...");
            String recomendacionIA = GroqService.consultarGroq(prompt.toString());

            // Crear y guardar la alerta
            AlertaIA alerta = AlertaIA.builder()
                    .idPaciente(idPaciente)
                    .fechaDisparo(LocalDateTime.now())
                    .motivoAlerta("Mediciones alteradas por " + diasConsecutivos + " días consecutivos")
                    .recomendacionIa(recomendacionIA)
                    .leida(false)
                    .build();

            Long idAlerta = AlertaService.guardarAlerta(alerta);

            logger.info("✓ Alerta generada exitosamente con ID: " + idAlerta + " para paciente ID: " + idPaciente);
            return true;

        } catch (Exception e) {
            logger.severe("Error al generar alerta para paciente ID " + idPaciente + ": " + e.getMessage());
            return false;
        }
    }
}
