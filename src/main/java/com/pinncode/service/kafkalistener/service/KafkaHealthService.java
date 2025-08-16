package com.pinncode.service.kafkalistener.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de monitoreo de salud para la conectividad con Kafka.
 * <p>
 * Implementa {@link HealthIndicator} de Spring Boot Actuator para reportar el estado
 * de la conexión con el clúster Kafka a través del endpoint {@code /actuator/health}.
 * Realiza verificaciones periódicas de conectividad usando el {@link AdminClient}
 * y expone el estado actual mediante métodos públicos.
 * <p>
 * Características principales:
 * <ul>
 *   <li>Verificación programada cada {@code kafka.connection.health-check.interval} ms (por defecto 60s).</li>
 *   <li>Timeout configurable para las operaciones de verificación.</li>
 *   <li>Reintentos automáticos con backoff exponencial en caso de fallos.</li>
 *   <li>Posibilidad de desactivar completamente las verificaciones.</li>
 *   <li>Estado volátil thread-safe para acceso concurrente.</li>
 * </ul>
 * <p>
 * Propiedades relevantes:
 * <ul>
 *   <li>{@code kafka.connection.health-check.enabled} - habilita/deshabilita verificaciones (por defecto true)</li>
 *   <li>{@code kafka.connection.health-check.timeout} - timeout en ms para operaciones (por defecto 30s)</li>
 *   <li>{@code kafka.connection.health-check.interval} - intervalo entre verificaciones en ms (por defecto 60s)</li>
 *   <li>{@code kafka.connection.retry.max-attempts} - intentos máximos de reconexión</li>
 *   <li>{@code kafka.connection.retry.initial-interval} - intervalo inicial de reintento</li>
 *   <li>{@code kafka.connection.retry.max-interval} - intervalo máximo de reintento</li>
 *   <li>{@code kafka.connection.retry.multiplier} - multiplicador de backoff</li>
 * </ul>
 *
 * @see HealthIndicator
 * @see AdminClient
 * @see Retryable
 * @see Scheduled
 */
@Slf4j
@Service
public class KafkaHealthService implements HealthIndicator {

    /** Cliente de administración de Kafka para verificaciones de conectividad */
    @Autowired
    private AdminClient kafkaAdminClient;

    /** Habilita o deshabilita las verificaciones de salud */
    @Value("${kafka.connection.health-check.enabled:true}")
    private boolean healthCheckEnabled;

    /** Timeout en milisegundos para las operaciones de verificación de salud */
    @Value("${kafka.connection.health-check.timeout:30000}")
    private long healthCheckTimeout;

    /** Estado actual de disponibilidad de Kafka (thread-safe) */
    private volatile boolean kafkaAvailable = false;
    
    /** Último error registrado durante las verificaciones (thread-safe) */
    private volatile String lastError = "";
    
    /** Timestamp de la última verificación realizada (thread-safe) */
    private volatile long lastCheckTime = 0;

    /**
     * Implementación del contrato {@link HealthIndicator} de Spring Boot Actuator.
     * <p>
     * Reporta el estado de salud de la conexión con Kafka basándose en la última
     * verificación realizada por {@link #checkKafkaHealth()}. Si las verificaciones
     * están deshabilitadas ({@code kafka.connection.health-check.enabled=false}),
     * siempre retorna {@code UP}.
     * <p>
     * Estados posibles:
     * <ul>
     *   <li>{@code UP} - Kafka disponible o verificaciones deshabilitadas</li>
     *   <li>{@code DOWN} - Kafka no disponible o error en verificación</li>
     * </ul>
     * Detalles incluidos en la respuesta:
     * <ul>
     *   <li>{@code kafka} - estado de la conexión ("Connected"/"Disconnected")</li>
     *   <li>{@code lastCheck} - timestamp de la última verificación</li>
     *   <li>{@code error} - mensaje del último error (solo si hay fallo)</li>
     *   <li>{@code status} - estado descriptivo</li>
     * </ul>
     *
     * @return {@link Health} con estado UP/DOWN y detalles de la conexión
     * @see HealthIndicator#health()
     */
    @Override
    public Health health() {

        var kafka = "kafka";
        var lastCheck = "lastCheck";

        if (!healthCheckEnabled) {
            return Health.up().withDetail("status", "Health check disabled").build();
        }

        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastCheckTime), ZoneId.systemDefault());

        try {

            if (kafkaAvailable) {
                return Health.up().withDetail(kafka, "Connected").withDetail(lastCheck, localDateTime).build();
            } else {
                return Health.down().withDetail(kafka, "Disconnected").withDetail("error", lastError)
                        .withDetail(lastCheck, localDateTime).build();
            }

        } catch (Exception e) {

            return Health.down().withDetail(kafka, "Error checking health").withDetail(lastCheck, localDateTime)
                    .withDetail("error", e.getMessage()).build();
        }
    }

    /**
     * Realiza verificación periódica de la conectividad con Kafka.
     * <p>
     * Este método se ejecuta automáticamente según el intervalo configurado
     * ({@code kafka.connection.health-check.interval}, por defecto 60s). Utiliza
     * {@link AdminClient#describeCluster()} para verificar la disponibilidad del
     * clúster y actualiza el estado interno.
     * <p>
     * Comportamiento:
     * <ul>
     *   <li>Si las verificaciones están deshabilitadas, termina inmediatamente.</li>
     *   <li>Realiza una operación {@code describeCluster()} con timeout configurable.</li>
     *   <li>Actualiza {@link #kafkaAvailable}, {@link #lastError} y {@link #lastCheckTime}.</li>
     *   <li>Registra cambios de estado (conectado/desconectado) en los logs.</li>
     *   <li>En caso de fallo, programa una reconexión vía {@link #scheduleReconnect()}.</li>
     * </ul>
     * Configuración de reintentos:
     * <ul>
     *   <li>Máximo 3 intentos con backoff exponencial (5s, 10s, 20s).</li>
     *   <li>Solo reintenta en caso de {@link Exception}.</li>
     * </ul>
     *
     * @see Scheduled
     * @see Retryable
     * @see AdminClient#describeCluster()
     */
    @Scheduled(fixedDelayString = "${kafka.connection.health-check.interval:60000}")
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 5000, multiplier = 2)
    )
    public void checkKafkaHealth() {

        if (!healthCheckEnabled) {
            return;
        }

        log.info("Validando conexión con Kafka...");
        lastCheckTime = System.currentTimeMillis();

        try {

            DescribeClusterResult clusterResult = kafkaAdminClient.describeCluster();

            clusterResult.clusterId().get(healthCheckTimeout, TimeUnit.MILLISECONDS);

            if (!kafkaAvailable) {
                log.info("Conexión Kafka establecida exitosamente");
            }

            kafkaAvailable = true;
            lastError = "";

        } catch (Exception e) {

            log.warn("Error de conexión Kafka, intentando reconectar: ", e);

            kafkaAvailable = false;
            lastError = e.getMessage();

            scheduleReconnect();
        }
    }

    /**
     * Programa un reintento de reconexión con Kafka tras un fallo de conectividad.
     * <p>
     * Este método se invoca automáticamente desde {@link #checkKafkaHealth()} cuando
     * se detecta una pérdida de conexión. Implementa una estrategia de reintento
     * configurable con backoff exponencial.
     * <p>
     * Comportamiento:
     * <ul>
     *   <li>Espera 1 minuto antes de realizar el primer reintento.</li>
     *   <li>Invoca {@link #checkKafkaHealth()} para verificar la conectividad.</li>
     *   <li>Maneja interrupciones del hilo de manera adecuada.</li>
     * </ul>
     * Configuración de reintentos (extraída de propiedades):
     * <ul>
     *   <li>Intentos máximos: {@code kafka.connection.retry.max-attempts} (por defecto -1 = infinito).</li>
     *   <li>Intervalo inicial: {@code kafka.connection.retry.initial-interval} (por defecto 60s).</li>
     *   <li>Intervalo máximo: {@code kafka.connection.retry.max-interval} (por defecto 300s).</li>
     *   <li>Multiplicador: {@code kafka.connection.retry.multiplier} (por defecto 1.5).</li>
     * </ul>
     *
     * @see Retryable
     * @see Thread#sleep(long)
     */
    @Retryable(
        retryFor = {Exception.class},
        maxAttemptsExpression = "${kafka.connection.retry.max-attempts:-1}",
        backoff = @Backoff(
            delayExpression = "${kafka.connection.retry.initial-interval:60000}",
            maxDelayExpression = "${kafka.connection.retry.max-interval:300000}",
            multiplierExpression = "${kafka.connection.retry.multiplier:1.5}"
        )
    )
    public void scheduleReconnect() {

        log.info("Programando reintento de conexión con Kafka...");

        try {

            Thread.sleep(Duration.ofMinutes(1).toMillis());
            checkKafkaHealth();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            log.warn("Reconexión Kafka interrumpida: {}", e.getMessage());
        }
    }

    /**
     * Obtiene el estado actual de disponibilidad de Kafka.
     * <p>
     * Este método es thread-safe y retorna el estado más reciente determinado
     * por {@link #checkKafkaHealth()}. El valor puede cambiar concurrentemente
     * durante las verificaciones periódicas.
     *
     * @return {@code true} si Kafka está disponible, {@code false} en caso contrario
     * @see #checkKafkaHealth()
     */
    public boolean isKafkaAvailable() {
        return kafkaAvailable;
    }

    /**
     * Obtiene el mensaje del último error registrado durante las verificaciones.
     * <p>
     * Si no ha habido errores o la última verificación fue exitosa, retorna
     * una cadena vacía. El valor es thread-safe y se actualiza concurrentemente.
     *
     * @return mensaje del último error o cadena vacía si no hay errores
     * @see #checkKafkaHealth()
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Obtiene el timestamp de la última verificación de salud realizada.
     * <p>
     * El valor se actualiza en cada ejecución de {@link #checkKafkaHealth()},
     * independientemente del resultado. Útil para determinar la "frescura"
     * de los datos de estado.
     *
     * @return timestamp en milisegundos de la última verificación (System.currentTimeMillis())
     * @see #checkKafkaHealth()
     * @see System#currentTimeMillis()
     */
    public long getLastCheckTime() {
        return lastCheckTime;
    }
}