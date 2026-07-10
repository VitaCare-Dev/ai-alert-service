package com.grupo10.medicalrecommendationgeneratorfunction;

import com.grupo10.model.Enfermedad;
import com.grupo10.model.RecomendacionAlimentariaIA;
import com.grupo10.service.EnfermedadService;
import com.grupo10.service.GroqService;
import com.grupo10.service.RecomendacionService;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Cubre {@link AIMedicalRecommendationGeneratorFuction}: la construcción del
 * título y del prompt (lógica pura) y la orquestación completa de
 * {@code generarRecomendacionPaciente}, mockeando los servicios estáticos
 * (base de datos y Groq). Mismo patrón que {@code AIThresholdAnalyzerFuctionTest}.
 */
class AIMedicalRecommendationGeneratorFuctionTest {

    private final AIMedicalRecommendationGeneratorFuction function = new AIMedicalRecommendationGeneratorFuction();

    private static Enfermedad enfermedad(String nombre) {
        return Enfermedad.builder().idEnfermedad(1L).nombreEnfermedad(nombre).descripcion("desc").build();
    }

    // ---- generarTituloRecomendacion ----

    @Test
    void generarTituloRecomendacion_conUnaEnfermedad() {
        String titulo = function.generarTituloRecomendacion(List.of(enfermedad("Diabetes tipo 2")));
        assertEquals("Recomendaciones para Diabetes tipo 2", titulo);
    }

    @Test
    void generarTituloRecomendacion_conDosEnfermedades() {
        String titulo = function.generarTituloRecomendacion(
                List.of(enfermedad("Diabetes tipo 2"), enfermedad("Hipertensión")));
        assertEquals("Recomendaciones para Diabetes tipo 2 y Hipertensión", titulo);
    }

    @Test
    void generarTituloRecomendacion_conTresOMasEnfermedadesUsaUnTituloGenerico() {
        String titulo = function.generarTituloRecomendacion(
                List.of(enfermedad("Diabetes tipo 2"), enfermedad("Hipertensión"), enfermedad("Obesidad")));
        assertEquals("Recomendaciones Alimentarias Personalizadas", titulo);
    }

    // ---- generarRecomendacionConIA (construcción del prompt) ----

    @Test
    void generarRecomendacionConIA_incluyeConsejosEspecificosParaDiabetesHipertensionYColesterol() throws Exception {
        List<Enfermedad> enfermedades = List.of(
                enfermedad("Diabetes tipo 2"),
                enfermedad("Hipertensión"),
                enfermedad("Colesterol alto"),
                enfermedad("Obesidad"),
                enfermedad("Insuficiencia renal"));

        try (MockedStatic<GroqService> groqMock = mockStatic(GroqService.class)) {
            groqMock.when(() -> GroqService.consultarGroq(any())).thenAnswer(invocation -> invocation.getArgument(0));

            String prompt = function.generarRecomendacionConIA(enfermedades);

            assertTrue(prompt.contains("reducir carbohidratos simples"), "debe incluir el consejo de diabetes");
            assertTrue(prompt.contains("reducir sodio"), "debe incluir el consejo de hipertensión");
            assertTrue(prompt.contains("aumentar omega-3"), "debe incluir el consejo de colesterol");
            assertTrue(prompt.contains("controlar porciones"), "debe incluir el consejo de obesidad");
            assertTrue(prompt.contains("controlar proteínas"), "debe incluir el consejo renal");
        }
    }

    @Test
    void generarRecomendacionConIA_incluyeConsejosUsandoElSegundoTerminoDeCadaCondicionOr() throws Exception {
        // Cada regla usa un `||` (ej. "hipertens" o "presión"); el test anterior
        // solo ejercita el primer término de cada una. Este cubre el segundo,
        // para no dejar ramas del OR sin probar.
        List<Enfermedad> enfermedades = List.of(
                enfermedad("Presión arterial alta"),
                enfermedad("Dislipidemia"),
                enfermedad("Problema en el riñón"));

        try (MockedStatic<GroqService> groqMock = mockStatic(GroqService.class)) {
            groqMock.when(() -> GroqService.consultarGroq(any())).thenAnswer(invocation -> invocation.getArgument(0));

            String prompt = function.generarRecomendacionConIA(enfermedades);

            assertTrue(prompt.contains("reducir sodio"), "debe incluir el consejo de hipertensión vía 'presión'");
            assertTrue(prompt.contains("aumentar omega-3"), "debe incluir el consejo de colesterol vía 'dislipidemia'");
            assertTrue(prompt.contains("controlar proteínas"), "debe incluir el consejo renal vía 'riñón'");
        }
    }

    @Test
    void generarRecomendacionConIA_noAgregaConsejosEspecificosParaEnfermedadesSinReglaConocida() throws Exception {
        try (MockedStatic<GroqService> groqMock = mockStatic(GroqService.class)) {
            groqMock.when(() -> GroqService.consultarGroq(any())).thenAnswer(invocation -> invocation.getArgument(0));

            String prompt = function.generarRecomendacionConIA(List.of(enfermedad("Anemia")));

            assertTrue(prompt.contains("Anemia"));
            assertFalse(prompt.contains("Para diabetes"));
        }
    }

    // ---- generarRecomendacionPaciente (orquestación completa) ----

    @Test
    void generarRecomendacionPaciente_noGeneraNadaSiYaExisteUnaRecomendacionReciente() {
        try (MockedStatic<RecomendacionService> recomendacionMock = mockStatic(RecomendacionService.class)) {
            recomendacionMock.when(() -> RecomendacionService.existeRecomendacionReciente(1L, 2)).thenReturn(true);

            assertFalse(function.generarRecomendacionPaciente(1L));
        }
    }

    @Test
    void generarRecomendacionPaciente_noGeneraNadaSiElPacienteNoTieneEnfermedades() {
        try (MockedStatic<RecomendacionService> recomendacionMock = mockStatic(RecomendacionService.class);
                MockedStatic<EnfermedadService> enfermedadMock = mockStatic(EnfermedadService.class)) {
            recomendacionMock.when(() -> RecomendacionService.existeRecomendacionReciente(1L, 2)).thenReturn(false);
            enfermedadMock.when(() -> EnfermedadService.obtenerEnfermedadesPorPaciente(1L))
                    .thenReturn(Collections.emptyList());

            assertFalse(function.generarRecomendacionPaciente(1L));
        }
    }

    @Test
    void generarRecomendacionPaciente_generaYGuardaUnaRecomendacionCuandoCorresponde() {
        try (MockedStatic<RecomendacionService> recomendacionMock = mockStatic(RecomendacionService.class);
                MockedStatic<EnfermedadService> enfermedadMock = mockStatic(EnfermedadService.class);
                MockedStatic<GroqService> groqMock = mockStatic(GroqService.class)) {
            recomendacionMock.when(() -> RecomendacionService.existeRecomendacionReciente(1L, 2)).thenReturn(false);
            enfermedadMock.when(() -> EnfermedadService.obtenerEnfermedadesPorPaciente(1L))
                    .thenReturn(List.of(enfermedad("Diabetes tipo 2")));
            groqMock.when(() -> GroqService.consultarGroq(any())).thenReturn("Evita azúcares refinados.");
            recomendacionMock.when(() -> RecomendacionService.guardarRecomendacion(any(RecomendacionAlimentariaIA.class)))
                    .thenReturn(99L);

            assertTrue(function.generarRecomendacionPaciente(1L));

            recomendacionMock.verify(
                    () -> RecomendacionService.guardarRecomendacion(any(RecomendacionAlimentariaIA.class)), times(1));
        }
    }

    @Test
    void generarRecomendacionPaciente_devuelveFalseSiFallaLaConsultaALaIA() {
        try (MockedStatic<RecomendacionService> recomendacionMock = mockStatic(RecomendacionService.class);
                MockedStatic<EnfermedadService> enfermedadMock = mockStatic(EnfermedadService.class);
                MockedStatic<GroqService> groqMock = mockStatic(GroqService.class)) {
            recomendacionMock.when(() -> RecomendacionService.existeRecomendacionReciente(1L, 2)).thenReturn(false);
            enfermedadMock.when(() -> EnfermedadService.obtenerEnfermedadesPorPaciente(1L))
                    .thenReturn(List.of(enfermedad("Diabetes tipo 2")));
            groqMock.when(() -> GroqService.consultarGroq(any())).thenThrow(new RuntimeException("Groq caído"));

            assertFalse(function.generarRecomendacionPaciente(1L));
            recomendacionMock.verify(() -> RecomendacionService.guardarRecomendacion(any()), never());
        }
    }
}
