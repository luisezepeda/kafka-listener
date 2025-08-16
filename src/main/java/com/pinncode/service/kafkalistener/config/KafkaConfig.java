package com.pinncode.service.kafkalistener.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuración central de Kafka para la aplicación.
 * <p>
 * Expone beans de infraestructura para:
 * <ul>
 *   <li>Consumidores Kafka ({@link #consumerFactory()})</li>
 *   <li>Fábrica de contenedores de listeners ({@link #kafkaListenerContainerFactory()})</li>
 *   <li>Manejador de errores común para listeners ({@link #kafkaErrorHandler()})</li>
 *   <li>Plantilla de reintentos genérica ({@link #retryTemplate()})</li>
 *   <li>Cliente de administración de Kafka ({@link #kafkaAdminClient()})</li>
 * </ul>
 * Las propiedades externas se obtienen desde {@code application.yml} mediante {@link Value}.
 * <p>
 * Propiedades relevantes utilizadas:
 * <ul>
 *   <li>{@code kafka.server}</li>
 *   <li>{@code kafka.consumer.group-id}</li>
 *   <li>{@code kafka.consumer.auto-offset-reset} (p. ej. {@code earliest} | {@code latest})</li>
 *   <li>{@code kafka.consumer.enable-auto-commit}</li>
 *   <li>{@code kafka.consumer.max-poll-records}</li>
 *   <li>{@code kafka.consumer.session-timeout-ms}</li>
 *   <li>{@code kafka.consumer.heartbeat-interval-ms}</li>
 *   <li>{@code kafka.connection.retry.enabled}</li>
 *   <li>{@code kafka.connection.retry.max-attempts}</li>
 *   <li>{@code kafka.connection.retry.initial-interval}</li>
 *   <li>{@code kafka.connection.retry.max-interval}</li>
 *   <li>{@code kafka.connection.retry.multiplier}</li>
 *   <li>{@code kafka.connection.timeout.connection-timeout}</li>
 *   <li>{@code kafka.connection.timeout.request-timeout}</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableKafka
@EnableRetry
@EnableScheduling
public class KafkaConfig {

    /** Servidor bootstrap de Kafka */
    @Value("${kafka.server}")
    private String bootstrapServers;

    /** ID de grupo del consumidor */
    @Value("${kafka.consumer.group-id}")
    private String groupId;

    /** Estrategia de reseteo de offset (latest, earliest) */
    @Value("${kafka.consumer.auto-offset-reset}")
    private String autoOffsetReset;

    /** Habilita el auto-commit de offsets */
    @Value("${kafka.consumer.enable-auto-commit}")
    private boolean enableAutoCommit;

    /** Máximo de registros por poll */
    @Value("${kafka.consumer.max-poll-records}")
    private int maxPollRecords;

    /** Timeout de sesión del consumidor */
    @Value("${kafka.consumer.session-timeout-ms}")
    private int sessionTimeout;

    /** Intervalo de heartbeat del consumidor */
    @Value("${kafka.consumer.heartbeat-interval-ms}")
    private int heartbeatInterval;

    /** Habilita reintentos de conexión */
    @Value("${kafka.connection.retry.enabled:true}")
    private boolean retryEnabled;

    /** Máximo de intentos de reintento */
    @Value("${kafka.connection.retry.max-attempts:3}")
    private int maxRetryAttempts;

    /** Intervalo inicial de reintento (ms) */
    @Value("${kafka.connection.retry.initial-interval:60000}")
    private long initialRetryInterval;

    /** Intervalo máximo de reintento (ms) */
    @Value("${kafka.connection.retry.max-interval:300000}")
    private long maxRetryInterval;

    /** Multiplicador de intervalo de reintento */
    @Value("${kafka.connection.retry.multiplier:1.5}")
    private double retryMultiplier;

    /** Timeout de conexión (ms) */
    @Value("${kafka.connection.timeout.connection-timeout:30000}")
    private int connectionTimeout;

    /** Timeout de solicitud (ms) */
    @Value("${kafka.connection.timeout.request-timeout:60000}")
    private int requestTimeout;

    /**
     * Crea una {@link ConsumerFactory} parametrizada a {@code <String, String>} con
     * la configuración de consumidor estándar para la aplicación.
     * <p>
     * Ajustes destacados aplicados en las propiedades del consumidor ({@link ConsumerConfig}):
     * <ul>
     *   <li>{@link ConsumerConfig#BOOTSTRAP_SERVERS_CONFIG}: hosts del clúster ({@code kafka.server}).</li>
     *   <li>{@link ConsumerConfig#GROUP_ID_CONFIG}: grupo de consumo ({@code kafka.consumer.group-id}).</li>
     *   <li>{@link ConsumerConfig#AUTO_OFFSET_RESET_CONFIG}: estrategia de offset ({@code kafka.consumer.auto-offset-reset}).</li>
     *   <li>{@link ConsumerConfig#ENABLE_AUTO_COMMIT_CONFIG}: commit automático de offsets ({@code kafka.consumer.enable-auto-commit}).</li>
     *   <li>{@link ConsumerConfig#MAX_POLL_RECORDS_CONFIG}: número máximo de registros por poll ({@code kafka.consumer.max-poll-records}).</li>
     *   <li>{@link ConsumerConfig#SESSION_TIMEOUT_MS_CONFIG} y {@link ConsumerConfig#HEARTBEAT_INTERVAL_MS_CONFIG}: estabilidad del grupo.</li>
     *   <li>Deserializadores de clave/valor establecidos a {@link StringDeserializer}.</li>
     *   <li>Parámetros de reconexión/retroceso: {@code request.timeout.ms}, {@code retry.backoff.ms},
     *       {@code reconnect.backoff.ms} y {@code reconnect.backoff.max.ms} derivados de las propiedades de conexión.</li>
     *   <li>Metadatos: {@code metadata.max.age.ms} y {@code fetch.max.wait.ms} para equilibrio entre latencia y rendimiento.</li>
     * </ul>
     * Esta fábrica se utiliza por los contenedores de listeners generados por
     * {@link #kafkaListenerContainerFactory()}.
     *
     * @return Fábrica de consumidores inicializada con las propiedades externas
     * @see DefaultKafkaConsumerFactory
     * @see ConsumerConfig
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Configuración básica
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeout);
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatInterval);

        // Deserializadores
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // Configuración de reconexión y reintentos
        configProps.put(ConsumerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 300000);
        configProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeout);
        configProps.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, initialRetryInterval);
        configProps.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, initialRetryInterval);
        configProps.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, maxRetryInterval);

        // Configuración de metadatos y descubrimiento
        configProps.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, 300000);
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Construye una {@link ConcurrentKafkaListenerContainerFactory} para listeners anotados
     * con {@code @KafkaListener}.
     * <p>
     * Características principales del contenedor:
     * <ul>
     *   <li>Ack manual inmediato: {@link ContainerProperties.AckMode#MANUAL_IMMEDIATE} para control explícito del commit.</li>
     *   <li>Tiempo máximo de espera de poll: 30s, reduciendo wakeups innecesarios en baja carga.</li>
     *   <li>{@code missingTopicsFatal = false}: no falla el arranque si el tópico aún no existe.</li>
     *   <li>Si {@code kafka.connection.retry.enabled} es verdadero, se habilita el {@link #kafkaErrorHandler()} como manejador común.</li>
     * </ul>
     * La concurrencia (número de hilos/particiones) puede ajustarse externamente si se requiere
     * mediante {@code factory.setConcurrency(n)} en otra configuración o perfil.
     *
     * @return Fábrica de contenedores lista para inyectarse en los listeners de Kafka
     * @see ConcurrentKafkaListenerContainerFactory
     * @see ContainerProperties
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // Configuración del contenedor
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setPollTimeout(30000);
        factory.getContainerProperties().setMissingTopicsFatal(false);

        if (retryEnabled) {
            factory.setCommonErrorHandler(kafkaErrorHandler());
        }

        return factory;
    }

    /**
     * Provee un {@link DefaultErrorHandler} para el procesamiento de errores en listeners Kafka.
     * <p>
     * Comportamiento:
     * <ul>
     *   <li>Backoff fijo de 5 segundos entre intentos, con un máximo de reintentos igual a
     *       {@code kafka.connection.retry.max-attempts} (valor mínimo 3).
     *   </li>
     *   <li>Marca como no reintentables: {@link IllegalArgumentException}, {@link NullPointerException},
     *       {@code com.fasterxml.jackson.core.JsonParseException} y {@code com.fasterxml.jackson.databind.JsonMappingException}.
     *       Estos errores suelen ser definitivos (datos mal formados) y se reportan sin reintento.</li>
     *   <li>Registra cada reintento con el valor del mensaje y el número de intento.</li>
     * </ul>
     * Sugerencia: para estrategias de DLT (Dead-Letter Topic) se puede extender esta definición
     * agregando un {@code DeadLetterPublishingRecoverer} como recuperador del {@link DefaultErrorHandler}.
     *
     * @return Manejador de errores configurado para ser usado por {@link #kafkaListenerContainerFactory()}
     * @see DefaultErrorHandler
     * @see FixedBackOff
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        // Configurar backoff más conservador para evitar problemas de seek
        BackOff backOff = new FixedBackOff(5000L, maxRetryAttempts > 0 ? maxRetryAttempts : 3L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(backOff);

        // Configurar qué excepciones no deben reintentarse
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            NullPointerException.class,
            com.fasterxml.jackson.core.JsonParseException.class,
            com.fasterxml.jackson.databind.JsonMappingException.class
        );

        errorHandler.setRetryListeners((recordValue, ex, deliveryAttempt) -> {
            log.error("Reintento {} para mensaje: {}", deliveryAttempt, recordValue.value());
            log.error("Error: {}", ex.getMessage());
        });

        return errorHandler;
    }

    /**
     * Define un {@link RetryTemplate} reutilizable para operaciones críticas de negocio o
     * integraciones puntuales.
     * <p>
     * Configuración aplicada:
     * <ul>
     *   <li>{@link SimpleRetryPolicy} con un máximo de intentos igual a
     *       {@code kafka.connection.retry.max-attempts} (valor mínimo 3).</li>
     *   <li>{@link FixedBackOffPolicy} con espera fija de 5 segundos entre intentos.</li>
     * </ul>
     * Este bean puede inyectarse en servicios que requieran lógica de reintento explícita
     * (por ejemplo, llamadas a APIs o a {@code AdminClient}).
     *
     * @return Plantilla de reintentos con política conservadora y backoff fijo
     * @see RetryTemplate
     * @see SimpleRetryPolicy
     * @see FixedBackOffPolicy
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Política de reintentos más conservadora
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxRetryAttempts > 0 ? maxRetryAttempts : 3);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Política de backoff fijo
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(5000L); // 5 segundos
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    /**
     * Crea un {@link AdminClient} para ejecutar operaciones administrativas en el clúster Kafka
     * (p. ej., creación/listado de tópicos, verificación de disponibilidad de brokers, etc.).
     * <p>
     * Reutiliza los mismos parámetros de conexión/retroceso que el consumidor para comportamiento
     * consistente ante fallos transitorios.
     * <p>
     * El ciclo de vida del cliente es gestionado por el contenedor de Spring; se cerrará al
     * finalizar el contexto de la aplicación.
     *
     * @return Cliente de administración inicializado apuntando a {@code kafka.server}
     * @see AdminClient
     * @see AdminClientConfig
     */
    @Bean
    public AdminClient kafkaAdminClient() {

        Properties props = new Properties();

        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 300000);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeout);
        props.put(AdminClientConfig.RETRY_BACKOFF_MS_CONFIG, initialRetryInterval);
        props.put(AdminClientConfig.RECONNECT_BACKOFF_MS_CONFIG, initialRetryInterval);
        props.put(AdminClientConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, maxRetryInterval);

        return AdminClient.create(props);
    }
}