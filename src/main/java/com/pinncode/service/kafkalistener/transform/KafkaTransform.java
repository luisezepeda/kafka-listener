package com.pinncode.service.kafkalistener.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pinncode.service.kafkalistener.model.KafkaEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Transformador para convertir payloads JSON de Kafka en objetos de dominio.
 * <p>
 * Utiliza Jackson ObjectMapper para deserializar cadenas JSON recibidas
 * desde Kafka hacia objetos {@link KafkaEvent}. Incluye soporte para
 * tipos de fecha/hora de Java 8+ mediante {@link JavaTimeModule}.
 * <p>
 * Características:
 * <ul>
 *   <li>Deserialización JSON a objeto tipado</li>
 *   <li>Soporte para LocalDateTime, Instant, etc.</li>
 *   <li>Manejo robusto de errores de parsing</li>
 *   <li>Logging detallado para debugging</li>
 * </ul>
 */
@Slf4j
@Component
public class KafkaTransform {

    /**
     * Transforma un payload JSON de Kafka a un objeto {@link KafkaEvent}.
     * <p>
     * Utiliza Jackson ObjectMapper configurado con {@link JavaTimeModule}
     * para manejar correctamente tipos de fecha/hora modernos. En caso de
     * error de parsing, registra el error y retorna {@code null}.
     * <p>
     * Proceso de transformación:
     * <ol>
     *   <li>Configura ObjectMapper con módulo de tiempo Java 8+</li>
     *   <li>Deserializa JSON a objeto KafkaEvent</li>
     *   <li>Registra éxito y objeto resultante (debug level)</li>
     *   <li>En caso de error, registra excepción y retorna null</li>
     * </ol>
     *
     * @param eventPayload cadena JSON recibida desde Kafka
     * @return objeto {@link KafkaEvent} deserializado o {@code null} si hay error
     * @see ObjectMapper#readValue(String, Class)
     * @see JavaTimeModule
     */
    public KafkaEvent kafkaEventStringToObjectTransform (String eventPayload) {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        try {

            KafkaEvent kafkaEvent = objectMapper.readValue(eventPayload, KafkaEvent.class);

            log.info("Se ha transformado el payload del evento a un objeto de manera correcta");

            log.debug(kafkaEvent.toString());

            return kafkaEvent;

        } catch (JsonProcessingException e) {

            log.error("Error al parsear el payload del evento kafka: ", e);
        }

        return null;
    }
}
