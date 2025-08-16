package com.pinncode.service.kafkalistener.service;

import com.pinncode.service.kafkalistener.model.KafkaEvent;

/**
 * Interface para el servicio de procesamiento de mensajes Kafka.
 */
public interface IProcessEventService {
    
    /**
     * Procesa un mensaje/evento de Kafka recibido.
     * 
     * @param kafkaEvent El mensaje/evento a procesar
     */
    void processEvent(KafkaEvent kafkaEvent);
}