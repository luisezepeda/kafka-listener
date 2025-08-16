package com.pinncode.service.kafkalistener.service;

import com.pinncode.service.kafkalistener.exception.KafkaException;
import com.pinncode.service.kafkalistener.model.KafkaEvent;
import com.pinncode.service.kafkalistener.transform.KafkaTransform;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Servicio principal para el consumo y procesamiento de eventos Kafka.
 * <p>
 * Escucha mensajes del tópico configurado y los procesa de manera asíncrona.
 * Incluye manejo de errores, validaciones de conectividad y logging detallado.
 * <p>
 * Características:
 * <ul>
 *   <li>Consumo automático desde {@code kafka.consumer.topic}</li>
 *   <li>Acknowledgment manual para control de offsets</li>
 *   <li>Verificación de estado de Kafka antes del procesamiento</li>
 *   <li>Transformación de payload a objeto {@link KafkaEvent}</li>
 *   <li>Delegación del procesamiento a {@link IProcessEventService}</li>
 * </ul>
 */
@Slf4j
@Service
public class KafkaListenerService implements IKafkaListenerService {

    /** Servicio para verificar el estado de conectividad con Kafka */
    @Autowired
    private KafkaHealthService kafkaHealthService;

    /** Servicio para procesar eventos de negocio */
    @Autowired
    private IProcessEventService processEventService;

    /** Transformador para convertir payload JSON a objetos KafkaEvent */
    @Autowired
    private KafkaTransform kafkaTransform;

    /**
     * Listener principal para consumir eventos desde Kafka.
     * <p>
     * Procesa mensajes del tópico configurado con las siguientes validaciones:
     * <ol>
     *   <li>Verifica conectividad con Kafka</li>
     *   <li>Valida que el payload no esté vacío</li>
     *   <li>Transforma el JSON a objeto {@link KafkaEvent}</li>
     *   <li>Delega el procesamiento al servicio de negocio</li>
     *   <li>Confirma el mensaje (acknowledge)</li>
     * </ol>
     * 
     * En caso de errores de Kafka, relanza la excepción para reintento.
     * Para otros errores, confirma el mensaje para evitar loops infinitos.
     *
     * @param consumerRecord registro completo del consumidor con metadatos
     * @param topic nombre del tópico origen (inyectado por Spring)
     * @param partition partición del mensaje (inyectado por Spring)
     * @param timestamp timestamp del mensaje (inyectado por Spring)
     * @param acknowledgment objeto para confirmar el procesamiento
     * @throws KafkaException si hay problemas de conectividad (se reintenta)
     */
    @KafkaListener(
        topics = "${kafka.consumer.topic}",
        groupId = "${kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void event(ConsumerRecord<String, String> consumerRecord,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        String eventPayload = consumerRecord.value();

        logEvent(topic, partition, timestamp, eventPayload);

        try {

            if (!kafkaHealthService.isKafkaAvailable()) {
                log.warn("Kafka no está disponible, el mensaje del evento será reintentado.");
                throw new KafkaException("Kafka connection not available");
            }

            if (StringUtils.isBlank(eventPayload)) {
                log.warn("Mensaje/evento vacío, confirmación de lectura, se omite procesamiento de evento");
                acknowledgment.acknowledge();
                return;
            }

            KafkaEvent kafkaEvent = kafkaTransform.kafkaEventStringToObjectTransform(eventPayload);

            if (kafkaEvent == null) {
                log.error("Error al transformar el payload del evento, confirmación de lectura, se omite procesamiento");
                acknowledgment.acknowledge();
                return;
            }

            processEventService.processEvent(kafkaEvent);

            acknowledgment.acknowledge();

            log.info("Mensaje/evento kafka procesado");

            log.info("----------------- End Evento Kafka -----------------");

        } catch (KafkaException e) {

            log.error("Error de conexión kafka: ", e);
            throw e;
            
        } catch (Exception e) {

            log.error("Error inesperado durante el procesamiento del evento :", e);
            acknowledgment.acknowledge();
        }
    }

    /**
     * Registra los detalles del evento recibido en los logs.
     * <p>
     * Formatea la información del mensaje de manera legible incluyendo:
     * timestamp convertido a fecha local, tópico, partición y payload.
     * Útil para trazabilidad y debugging.
     *
     * @param topic nombre del tópico origen
     * @param partition número de partición
     * @param timestamp timestamp en milisegundos (epoch)
     * @param eventPayload contenido del mensaje
     */
    private void logEvent (String topic, Integer partition, Long timestamp, String eventPayload) {

        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());

        log.info("---------------- Start Evento Kafka ----------------");
        log.info("Topic: {}", topic);
        log.info("Partition: {}", partition);
        log.info("Timestamp: {} ({})", timestamp, localDateTime);
        log.info("Event payload: {}", eventPayload);
    }
}