package com.pinncode.service.kafkalistener.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;

/**
 * Servicio para escuchar y procesar mensajes de Kafka.
 * <p>
 * Define el contrato para consumir mensajes de Kafka, incluyendo los detalles del mensaje
 * y la confirmación de procesamiento.
 */
public interface IKafkaListenerService {

    /**
     * Método para consumir mensajes/eventos de Kafka.
     *
     * @param consumerRecord El registro del consumidor que contiene el mensaje.
     * @param topic El topic del mensaje recibido.
     * @param partition La partición del mensaje recibido.
     * @param timestamp La marca de tiempo del mensaje recibido.
     * @param acknowledgment Acknowledgment para confirmar el procesamiento del mensaje.
     */
    void event(ConsumerRecord<String, String> consumerRecord,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment);
}
