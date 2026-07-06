# ai-alert-service

Servicio de IA proactiva de **VitaCare**, implementado como **Azure Functions** (no un microservicio Spring Boot tradicional). Analiza periódicamente las mediciones de salud de los pacientes contra sus umbrales médicos para generar alertas, y genera recomendaciones alimentarias según sus enfermedades crónicas, usando la API de Groq como motor de IA.

## Tabla de contenidos

- [Arquitectura](#arquitectura)
- [Stack tecnológico](#stack-tecnológico)
- [Configuración](#configuración)
- [Ejecución local](#ejecución-local)
- [Funciones](#funciones)
- [Despliegue](#despliegue)
- [Estructura del proyecto](#estructura-del-proyecto)

## Arquitectura

A diferencia de los demás microservicios, este servicio combina **funciones programadas (timer trigger)** que corren en segundo plano, con **funciones HTTP** que expone [`bff-vitacare`](../bff-vitacare) (usando una function key, nunca expuesta a la app móvil).

```
                 ┌────────────────────────────┐
  (cada 6h) ────▶│  AIThresholdAnalyzer        │──▶ tb_alerta_ia
  (timer)        │  compara mediciones vs.     │
                 │  umbrales médicos           │
                 └────────────────────────────┘

                 ┌────────────────────────────┐
  (cada 3 días) ▶│ AIMedicalRecommendationGen. │──▶ tb_recomendacion_alimentaria_ia
  (timer)        │  genera recomendaciones     │
                 │  vía Groq según enfermedad  │
                 └────────────────────────────┘

  bff-vitacare ─▶ Funciones HTTP (consulta y marcar como leído) ─▶ Oracle Database
```

Se conecta **directamente a Oracle** (sin Spring Data JPA) usando JDBC + HikariCP.

## Stack tecnológico

| Componente | Detalle |
|---|---|
| Lenguaje | Java 21 |
| Plataforma | Azure Functions (Consumption plan) |
| Persistencia | JDBC directo + HikariCP (Oracle, driver `ojdbc11`, con soporte de Oracle Wallet/TLS) |
| IA | API de Groq |
| Serialización | Jackson (con soporte para `java.time` vía `jackson-datatype-jsr310`) |
| Build | Maven + `azure-functions-maven-plugin` |

## Configuración

| Variable de entorno | Descripción |
|---|---|
| `DB_URL` | URL JDBC de la base de datos Oracle |
| `DB_USER` | Usuario de la base de datos |
| `DB_PASSWORD` | Contraseña de la base de datos |
| `GROQ_API_KEY` | API key de Groq, usada para generar alertas y recomendaciones |

Estas variables se configuran como **Application Settings** en el recurso de Azure Functions (o en `local.settings.json`, no versionado, para desarrollo local).

## Ejecución local

Requiere [Azure Functions Core Tools](https://learn.microsoft.com/azure/azure-functions/functions-run-local):

```bash
mvn clean package
mvn azure-functions:run
```

## Funciones

### Programadas (timer trigger)

| Función | Cron | Descripción |
|---|---|---|
| `AIThresholdAnalyzer` | `0 0 */6 * * *` (cada 6 horas) | Compara las mediciones recientes de cada paciente contra sus umbrales médicos y genera alertas cuando corresponde |
| `AIMedicalRecommendationGenerator` | `0 0 0 */3 * *` (cada 3 días) | Genera recomendaciones alimentarias vía Groq según las enfermedades crónicas del paciente |

### HTTP (consumidas por `bff-vitacare`, requieren function key)

| Función | Método | Ruta | Descripción |
|---|---|---|---|
| `AIThresholdAnalyzerManual` | HTTP | — | Dispara manualmente el análisis de umbrales para un paciente puntual |
| `AIMedicalRecommendationGeneratorManual` | HTTP | — | Dispara manualmente la generación de una recomendación |
| `ObtenerAlertas` | `GET` | `alertas/{idPaciente}` | Lista todas las alertas de un paciente |
| `ObtenerAlertasNoLeidas` | `GET` | `alertas/{idPaciente}/no-leidas` | Lista las alertas no leídas |
| `MarcarAlertaLeida` | `PUT` | `alertas/{idAlerta}/leer` | Marca una alerta como leída |
| `MarcarTodasAlertasLeidas` | `PUT` | `alertas/paciente/{idPaciente}/leer-todas` | Marca todas las alertas de un paciente como leídas |
| `ObtenerRecomendaciones` | `GET` | `recomendaciones/{idPaciente}` | Lista todas las recomendaciones de un paciente |
| `ObtenerRecomendacionesNoLeidas` | `GET` | `recomendaciones/{idPaciente}/no-leidas` | Lista las recomendaciones no leídas |
| `MarcarRecomendacionLeida` | `PUT` | `recomendaciones/{idRecomendacion}/leer` | Marca una recomendación como leída |
| `MarcarTodasRecomendacionesLeidas` | `PUT` | `recomendaciones/paciente/{idPaciente}/leer-todas` | Marca todas las recomendaciones como leídas |

## Despliegue

```bash
mvn clean package azure-functions:deploy
```

Configuración del recurso (definida en `pom.xml`):

| Propiedad | Valor |
|---|---|
| Resource Group | `rg-vitacare` |
| Región | `eastus` |
| App Service Plan | `ai-alert-service-plan` |
| Runtime | Java 21, Linux, Consumption |

## Estructura del proyecto

```
src/main/java/com/grupo10/
├── apifunction/                          # Funciones HTTP de consulta (alertas, recomendaciones)
├── config/                               # Configuración de conexión a base de datos (HikariCP)
├── medicalrecommendationgeneratorfunction/  # Función programada de recomendaciones
├── thresholdanalyzerfunction/             # Función programada de análisis de umbrales
├── model/                                # Modelos de dominio
├── service/                               # Lógica de negocio e integración con Groq
└── Function.java                         # Función de ejemplo/plantilla
```
