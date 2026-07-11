package com.grupo10.thresholdanalyzerfunction;

import com.grupo10.model.MedicionGlucosa;
import com.grupo10.model.MedicionVitales;
import com.grupo10.model.UmbralMedico;
import com.grupo10.model.AlertaIA;
import com.grupo10.service.AlertaService;
import com.grupo10.service.GroqService;
import com.grupo10.service.MedicionService;
import com.grupo10.service.UmbralService;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
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
 * Cubre el algoritmo de detección de mediciones alteradas por días
 * consecutivos (lógica pura, sin dependencias) y la orquestación completa de
 * {@link AIThresholdAnalyzerFuction#analizarPaciente}, mockeando los
 * servicios estáticos que hacen I/O real (base de datos y Groq).
 */
class AIThresholdAnalyzerFuctionTest {

    private final AIThresholdAnalyzerFuction function = new AIThresholdAnalyzerFuction();

    // ---- contarDiasConsecutivos ----

    @Test
    void contarDiasConsecutivos_devuelveElTamanoCuandoHayMenosDeDosFechas() {
        assertEquals(0, function.contarDiasConsecutivos(Collections.emptyList()));
        assertEquals(1, function.contarDiasConsecutivos(List.of(LocalDateTime.now())));
    }

    @Test
    void contarDiasConsecutivos_detectaDosDiasSeguidos() {
        List<LocalDateTime> fechas = Arrays.asList(
                LocalDateTime.of(2026, 7, 10, 8, 0),
                LocalDateTime.of(2026, 7, 9, 8, 0));

        assertEquals(2, function.contarDiasConsecutivos(fechas));
    }

    @Test
    void contarDiasConsecutivos_detectaTresDiasSeguidos() {
        List<LocalDateTime> fechas = Arrays.asList(
                LocalDateTime.of(2026, 7, 10, 8, 0),
                LocalDateTime.of(2026, 7, 9, 8, 0),
                LocalDateTime.of(2026, 7, 8, 8, 0));

        assertEquals(3, function.contarDiasConsecutivos(fechas));
    }

    @Test
    void contarDiasConsecutivos_noCuentaFechasConUnHuecoEntreMedio() {
        // 10 y 9 son consecutivos, pero 9 -> 5 tiene un salto de 4 días: la
        // racha se corta y el máximo consecutivo sigue siendo 2, no 3.
        List<LocalDateTime> fechas = Arrays.asList(
                LocalDateTime.of(2026, 7, 10, 8, 0),
                LocalDateTime.of(2026, 7, 9, 8, 0),
                LocalDateTime.of(2026, 7, 5, 8, 0));

        assertEquals(2, function.contarDiasConsecutivos(fechas));
    }

    @Test
    void contarDiasConsecutivos_ignoraFechasQueNoSonConsecutivas() {
        List<LocalDateTime> fechas = Arrays.asList(
                LocalDateTime.of(2026, 7, 10, 8, 0),
                LocalDateTime.of(2026, 7, 5, 8, 0));

        assertEquals(1, function.contarDiasConsecutivos(fechas));
    }

    // ---- contarMedicionesGlucosaAlteradas ----

    @Test
    void contarMedicionesGlucosaAlteradas_cuentaSobreElMaximoYBajoElMinimo() {
        UmbralMedico umbral = UmbralMedico.builder().glucosaMin(70).glucosaMax(180).build();
        List<MedicionGlucosa> mediciones = List.of(
                MedicionGlucosa.builder().glucosa(200).build(), // sobre el máximo
                MedicionGlucosa.builder().glucosa(50).build(), // bajo el mínimo
                MedicionGlucosa.builder().glucosa(100).build() // dentro de rango
        );

        assertEquals(2, function.contarMedicionesGlucosaAlteradas(mediciones, umbral));
    }

    @Test
    void contarMedicionesGlucosaAlteradas_ignoraMedicionesSinValor() {
        UmbralMedico umbral = UmbralMedico.builder().glucosaMin(70).glucosaMax(180).build();
        List<MedicionGlucosa> mediciones = List.of(
                MedicionGlucosa.builder().glucosa(null).build());

        assertEquals(0, function.contarMedicionesGlucosaAlteradas(mediciones, umbral));
    }

    @Test
    void contarMedicionesGlucosaAlteradas_noCuentaNadaSiElUmbralNoTieneLimites() {
        UmbralMedico umbral = UmbralMedico.builder().glucosaMin(null).glucosaMax(null).build();
        List<MedicionGlucosa> mediciones = List.of(
                MedicionGlucosa.builder().glucosa(9999).build());

        assertEquals(0, function.contarMedicionesGlucosaAlteradas(mediciones, umbral));
    }

    // ---- contarMedicionesVitalesAlteradas ----

    @Test
    void contarMedicionesVitalesAlteradas_detectaSistolicaDiastolicaYTemperaturaAlteradas() {
        UmbralMedico umbral = UmbralMedico.builder()
                .sistolicaMax(140)
                .diastolicaMax(90)
                .temperaturaMax(new BigDecimal("38.0"))
                .build();

        List<MedicionVitales> mediciones = List.of(
                MedicionVitales.builder().presionSistolica(150).build(), // sistólica alterada
                MedicionVitales.builder().presionDiastolica(95).build(), // diastólica alterada
                MedicionVitales.builder().temperatura(new BigDecimal("38.5")).build(), // temperatura alterada
                MedicionVitales.builder().presionSistolica(120).presionDiastolica(80)
                        .temperatura(new BigDecimal("36.5")).build() // normal
        );

        assertEquals(3, function.contarMedicionesVitalesAlteradas(mediciones, umbral));
    }

    @Test
    void contarMedicionesVitalesAlteradas_noCuentaNadaSiElUmbralNoTieneLimites() {
        UmbralMedico umbral = UmbralMedico.builder().build();
        List<MedicionVitales> mediciones = List.of(
                MedicionVitales.builder().presionSistolica(999).presionDiastolica(999)
                        .temperatura(new BigDecimal("50")).build());

        assertEquals(0, function.contarMedicionesVitalesAlteradas(mediciones, umbral));
    }

    @Test
    void contarMedicionesVitalesAlteradas_detectaHipotensionEHipotermia() {
        // DEF-AI-02: antes no existían umbrales mínimos, así que un paciente en
        // shock (presión muy baja) o con hipotermia nunca generaba alerta.
        UmbralMedico umbral = UmbralMedico.builder()
                .sistolicaMin(90)
                .diastolicaMin(60)
                .temperaturaMin(new BigDecimal("35.0"))
                .build();

        List<MedicionVitales> mediciones = List.of(
                MedicionVitales.builder().presionSistolica(60).build(), // hipotensión sistólica
                MedicionVitales.builder().presionDiastolica(40).build(), // hipotensión diastólica
                MedicionVitales.builder().temperatura(new BigDecimal("34.0")).build(), // hipotermia
                MedicionVitales.builder().presionSistolica(120).presionDiastolica(80)
                        .temperatura(new BigDecimal("36.5")).build() // normal
        );

        assertEquals(3, function.contarMedicionesVitalesAlteradas(mediciones, umbral));
    }

    // ---- obtenerFechasGlucosaAlterada / obtenerFechasVitalesAlteradas ----

    @Test
    void obtenerFechasGlucosaAlterada_devuelveSoloLasFechasDeMedicionesFueraDeRangoOrdenadasDescendente() {
        UmbralMedico umbral = UmbralMedico.builder().glucosaMin(70).glucosaMax(180).build();
        List<MedicionGlucosa> mediciones = List.of(
                MedicionGlucosa.builder().glucosa(100).fechaHora(LocalDateTime.of(2026, 7, 9, 8, 0)).build(), // normal
                MedicionGlucosa.builder().glucosa(300).fechaHora(LocalDateTime.of(2026, 7, 8, 8, 0)).build(), // alterada
                MedicionGlucosa.builder().glucosa(320).fechaHora(LocalDateTime.of(2026, 7, 10, 8, 0)).build() // alterada
        );

        List<LocalDateTime> resultado = function.obtenerFechasGlucosaAlterada(mediciones, umbral);

        assertEquals(List.of(
                LocalDateTime.of(2026, 7, 10, 8, 0),
                LocalDateTime.of(2026, 7, 8, 8, 0)), resultado);
    }

    @Test
    void obtenerFechasGlucosaAlterada_ignoraMedicionesSinFechaHora() {
        UmbralMedico umbral = UmbralMedico.builder().glucosaMax(180).build();
        List<MedicionGlucosa> mediciones = List.of(
                MedicionGlucosa.builder().glucosa(300).fechaHora(null).build());

        assertTrue(function.obtenerFechasGlucosaAlterada(mediciones, umbral).isEmpty());
    }

    @Test
    void obtenerFechasVitalesAlteradas_devuelveSoloLasFechasDeMedicionesFueraDeRango() {
        UmbralMedico umbral = UmbralMedico.builder().sistolicaMax(140).build();
        List<MedicionVitales> mediciones = List.of(
                MedicionVitales.builder().presionSistolica(120).fechaHora(LocalDateTime.of(2026, 7, 9, 8, 0)).build(),
                MedicionVitales.builder().presionSistolica(180).fechaHora(LocalDateTime.of(2026, 7, 10, 8, 0)).build());

        List<LocalDateTime> resultado = function.obtenerFechasVitalesAlteradas(mediciones, umbral);

        assertEquals(List.of(LocalDateTime.of(2026, 7, 10, 8, 0)), resultado);
    }

    // ---- analizarPaciente (orquestación completa) ----

    @Test
    void analizarPaciente_noGeneraAlertaSiElPacienteNoTieneUmbralesConfigurados() {
        try (MockedStatic<UmbralService> umbralMock = mockStatic(UmbralService.class)) {
            umbralMock.when(() -> UmbralService.obtenerUmbralesPorPaciente(1L)).thenReturn(null);

            assertFalse(function.analizarPaciente(1L));
        }
    }

    @Test
    void analizarPaciente_noGeneraAlertaSiSoloHayUnaMedicionAlteradaEnUnSoloDia() {
        UmbralMedico umbral = UmbralMedico.builder().glucosaMax(180).build();

        try (MockedStatic<UmbralService> umbralMock = mockStatic(UmbralService.class);
                MockedStatic<MedicionService> medicionMock = mockStatic(MedicionService.class)) {
            umbralMock.when(() -> UmbralService.obtenerUmbralesPorPaciente(1L)).thenReturn(umbral);
            medicionMock.when(() -> MedicionService.obtenerMedicionesGlucosaRecientes(1L, 3))
                    .thenReturn(List.of(MedicionGlucosa.builder().glucosa(300)
                            .fechaHora(LocalDateTime.of(2026, 7, 10, 8, 0)).build()));
            medicionMock.when(() -> MedicionService.obtenerMedicionesVitalesRecientes(1L, 3))
                    .thenReturn(Collections.emptyList());

            assertFalse(function.analizarPaciente(1L));
        }
    }

    @Test
    void analizarPaciente_noGeneraAlertaSiLasMedicionesAlteradasNoSonEnDiasConsecutivos() {
        // DEF-AI-01: dos mediciones de glucosa alteradas, pero en días muy
        // separados entre sí (no consecutivos) -> no debe generar alerta.
        UmbralMedico umbral = UmbralMedico.builder().glucosaMax(180).build();
        List<MedicionGlucosa> glucosaAlteradaNoConsecutiva = List.of(
                MedicionGlucosa.builder().glucosa(300).fechaHora(LocalDateTime.of(2026, 7, 10, 8, 0)).build(),
                MedicionGlucosa.builder().glucosa(320).fechaHora(LocalDateTime.of(2026, 7, 1, 8, 0)).build());

        try (MockedStatic<UmbralService> umbralMock = mockStatic(UmbralService.class);
                MockedStatic<MedicionService> medicionMock = mockStatic(MedicionService.class)) {
            umbralMock.when(() -> UmbralService.obtenerUmbralesPorPaciente(1L)).thenReturn(umbral);
            medicionMock.when(() -> MedicionService.obtenerMedicionesGlucosaRecientes(1L, 3))
                    .thenReturn(glucosaAlteradaNoConsecutiva);
            medicionMock.when(() -> MedicionService.obtenerMedicionesVitalesRecientes(1L, 3))
                    .thenReturn(Collections.emptyList());

            assertFalse(function.analizarPaciente(1L));
        }
    }

    @Test
    void analizarPaciente_noGeneraAlertaSiHayDiasConsecutivosConMedicionesPeroNoAlteradas() {
        // DEF-AI-01: el paciente sí tiene controles en días consecutivos, pero
        // ninguno está alterado -> no debe generar alerta (antes del fix, el
        // "día consecutivo" se calculaba sobre cualquier control, no sobre
        // los realmente alterados).
        UmbralMedico umbral = UmbralMedico.builder().glucosaMin(70).glucosaMax(180).build();
        List<MedicionGlucosa> glucosaNormal = List.of(
                MedicionGlucosa.builder().glucosa(100).fechaHora(LocalDateTime.of(2026, 7, 10, 8, 0)).build(),
                MedicionGlucosa.builder().glucosa(110).fechaHora(LocalDateTime.of(2026, 7, 9, 8, 0)).build());

        try (MockedStatic<UmbralService> umbralMock = mockStatic(UmbralService.class);
                MockedStatic<MedicionService> medicionMock = mockStatic(MedicionService.class)) {
            umbralMock.when(() -> UmbralService.obtenerUmbralesPorPaciente(1L)).thenReturn(umbral);
            medicionMock.when(() -> MedicionService.obtenerMedicionesGlucosaRecientes(1L, 3))
                    .thenReturn(glucosaNormal);
            medicionMock.when(() -> MedicionService.obtenerMedicionesVitalesRecientes(1L, 3))
                    .thenReturn(Collections.emptyList());

            assertFalse(function.analizarPaciente(1L));
        }
    }

    @Test
    void analizarPaciente_noGeneraUnaSegundaAlertaSiYaExisteUnaReciente() {
        UmbralMedico umbral = UmbralMedico.builder().glucosaMin(70).glucosaMax(180).build();
        List<MedicionGlucosa> glucosaAlterada = List.of(
                MedicionGlucosa.builder().glucosa(300).fechaHora(LocalDateTime.of(2026, 7, 10, 8, 0)).build(),
                MedicionGlucosa.builder().glucosa(320).fechaHora(LocalDateTime.of(2026, 7, 9, 8, 0)).build());

        try (MockedStatic<UmbralService> umbralMock = mockStatic(UmbralService.class);
                MockedStatic<MedicionService> medicionMock = mockStatic(MedicionService.class);
                MockedStatic<AlertaService> alertaMock = mockStatic(AlertaService.class)) {
            umbralMock.when(() -> UmbralService.obtenerUmbralesPorPaciente(1L)).thenReturn(umbral);
            medicionMock.when(() -> MedicionService.obtenerMedicionesGlucosaRecientes(1L, 3))
                    .thenReturn(glucosaAlterada);
            medicionMock.when(() -> MedicionService.obtenerMedicionesVitalesRecientes(1L, 3))
                    .thenReturn(Collections.emptyList());
            alertaMock.when(() -> AlertaService.existeAlertaReciente(1L, 24)).thenReturn(true);

            assertFalse(function.analizarPaciente(1L));
            alertaMock.verify(() -> AlertaService.guardarAlerta(any()), never());
        }
    }

    @Test
    void analizarPaciente_generaYGuardaUnaAlertaCuandoHayMedicionesAlteradasEnDiasConsecutivos() throws Exception {
        UmbralMedico umbral = UmbralMedico.builder().glucosaMin(70).glucosaMax(180).build();
        List<MedicionGlucosa> glucosaAlterada = List.of(
                MedicionGlucosa.builder().glucosa(300).fechaHora(LocalDateTime.of(2026, 7, 10, 8, 0)).build(),
                MedicionGlucosa.builder().glucosa(320).fechaHora(LocalDateTime.of(2026, 7, 9, 8, 0)).build());

        try (MockedStatic<UmbralService> umbralMock = mockStatic(UmbralService.class);
                MockedStatic<MedicionService> medicionMock = mockStatic(MedicionService.class);
                MockedStatic<AlertaService> alertaMock = mockStatic(AlertaService.class);
                MockedStatic<GroqService> groqMock = mockStatic(GroqService.class)) {
            umbralMock.when(() -> UmbralService.obtenerUmbralesPorPaciente(1L)).thenReturn(umbral);
            medicionMock.when(() -> MedicionService.obtenerMedicionesGlucosaRecientes(1L, 3))
                    .thenReturn(glucosaAlterada);
            medicionMock.when(() -> MedicionService.obtenerMedicionesVitalesRecientes(1L, 3))
                    .thenReturn(Collections.emptyList());
            alertaMock.when(() -> AlertaService.existeAlertaReciente(1L, 24)).thenReturn(false);
            groqMock.when(() -> GroqService.consultarGroq(any())).thenReturn("Consulta a tu médico pronto.");
            alertaMock.when(() -> AlertaService.guardarAlerta(any(AlertaIA.class))).thenReturn(42L);

            assertTrue(function.analizarPaciente(1L));

            alertaMock.verify(() -> AlertaService.guardarAlerta(any(AlertaIA.class)), times(1));
        }
    }

    @Test
    void analizarPaciente_generaAlertaCuandoSonLosSignosVitalesLosAlteradosYNoLaGlucosa() throws Exception {
        UmbralMedico umbral = UmbralMedico.builder().sistolicaMax(140).build();
        List<MedicionVitales> vitalesAlteradas = List.of(
                MedicionVitales.builder().presionSistolica(180).fechaHora(LocalDateTime.of(2026, 7, 10, 8, 0)).build(),
                MedicionVitales.builder().presionSistolica(190).fechaHora(LocalDateTime.of(2026, 7, 9, 8, 0)).build());

        try (MockedStatic<UmbralService> umbralMock = mockStatic(UmbralService.class);
                MockedStatic<MedicionService> medicionMock = mockStatic(MedicionService.class);
                MockedStatic<AlertaService> alertaMock = mockStatic(AlertaService.class);
                MockedStatic<GroqService> groqMock = mockStatic(GroqService.class)) {
            umbralMock.when(() -> UmbralService.obtenerUmbralesPorPaciente(1L)).thenReturn(umbral);
            medicionMock.when(() -> MedicionService.obtenerMedicionesGlucosaRecientes(1L, 3))
                    .thenReturn(Collections.emptyList());
            medicionMock.when(() -> MedicionService.obtenerMedicionesVitalesRecientes(1L, 3))
                    .thenReturn(vitalesAlteradas);
            alertaMock.when(() -> AlertaService.existeAlertaReciente(1L, 24)).thenReturn(false);
            groqMock.when(() -> GroqService.consultarGroq(any())).thenReturn("Consulta a tu médico pronto.");
            alertaMock.when(() -> AlertaService.guardarAlerta(any(AlertaIA.class))).thenReturn(43L);

            assertTrue(function.analizarPaciente(1L));

            alertaMock.verify(() -> AlertaService.guardarAlerta(any(AlertaIA.class)), times(1));
        }
    }

    @Test
    void analizarPaciente_devuelveFalseSiFallaLaGeneracionDeLaAlerta() throws Exception {
        // Un fallo al consultar la IA o guardar no debe propagar la excepción:
        // generarAlerta la atrapa y analizarPaciente devuelve false.
        UmbralMedico umbral = UmbralMedico.builder().glucosaMin(70).glucosaMax(180).build();
        List<MedicionGlucosa> glucosaAlterada = List.of(
                MedicionGlucosa.builder().glucosa(300).fechaHora(LocalDateTime.of(2026, 7, 10, 8, 0)).build(),
                MedicionGlucosa.builder().glucosa(320).fechaHora(LocalDateTime.of(2026, 7, 9, 8, 0)).build());

        try (MockedStatic<UmbralService> umbralMock = mockStatic(UmbralService.class);
                MockedStatic<MedicionService> medicionMock = mockStatic(MedicionService.class);
                MockedStatic<AlertaService> alertaMock = mockStatic(AlertaService.class);
                MockedStatic<GroqService> groqMock = mockStatic(GroqService.class)) {
            umbralMock.when(() -> UmbralService.obtenerUmbralesPorPaciente(1L)).thenReturn(umbral);
            medicionMock.when(() -> MedicionService.obtenerMedicionesGlucosaRecientes(1L, 3))
                    .thenReturn(glucosaAlterada);
            medicionMock.when(() -> MedicionService.obtenerMedicionesVitalesRecientes(1L, 3))
                    .thenReturn(Collections.emptyList());
            alertaMock.when(() -> AlertaService.existeAlertaReciente(1L, 24)).thenReturn(false);
            groqMock.when(() -> GroqService.consultarGroq(any())).thenThrow(new RuntimeException("Groq caído"));

            assertFalse(function.analizarPaciente(1L));
        }
    }
}
