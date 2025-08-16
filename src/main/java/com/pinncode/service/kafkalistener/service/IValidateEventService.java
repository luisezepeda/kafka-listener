package com.pinncode.service.kafkalistener.service;

import com.pinncode.service.kafkalistener.model.KafkaEvent;

/**
 * Interface para el servicio de validación de eventos Kafka.
 * <p>
 * Define el contrato para validar la estructura de los mensajes/eventos recibidos.
 */
public interface IValidateEventService {

    /**
     * Válida la estructura del mensaje/evento.
     *
     * @param kafkaEvent El mensaje/evento a validar
     * @return true si el mensaje/evento es válido, false en caso contrario
     */
    boolean checkBusinessRulesForTheEvent(KafkaEvent kafkaEvent);
}
