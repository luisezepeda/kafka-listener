package com.pinncode.service.kafkalistener.service;

import com.pinncode.service.kafkalistener.model.KafkaEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Servicio de procesamiento de eventos de negocio desde Kafka.
 * <p>
 * Implementa la lógica de negocio para procesar eventos {@link KafkaEvent}
 * recibidos a través del listener. Incluye validación de reglas de negocio
 * y manejo de errores específicos del dominio.
 * <p>
 * Flujo de procesamiento:
 * <ol>
 *   <li>Validación de reglas de negocio</li>
 *   <li>Procesamiento específico del evento</li>
 *   <li>Manejo de errores con propagación controlada</li>
 * </ol>
 */
@Slf4j
@Service
public class ProcessEventService implements IProcessEventService {

    /** Servicio para validar reglas de negocio del evento */
    @Autowired
    private IValidateEventService validateEventService;

    /**
     * Procesa un evento Kafka aplicando validaciones y lógica de negocio.
     * <p>
     * Ejecuta el flujo completo de procesamiento:
     * <ul>
     *   <li>Valida reglas de negocio mediante {@link IValidateEventService}</li>
     *   <li>Si la validación falla, omite el procesamiento</li>
     *   <li>Ejecuta la lógica de negocio específica del evento</li>
     *   <li>Propaga errores como {@link RuntimeException} para manejo upstream</li>
     * </ul>
     *
     * @param kafkaEvent evento a procesar con datos de negocio
     * @throws RuntimeException si ocurre un error durante el procesamiento
     */
    @Override
    public void processEvent(KafkaEvent kafkaEvent) {

        log.info("Inicia flujo de negocio");
        
        try {

            final Boolean validInformation = validateEventService.checkBusinessRulesForTheEvent(kafkaEvent);

            if (Boolean.FALSE.equals(validInformation)) {
                log.error("Información inválida o incompleta, se omite el procesamiento");
                return;
            }

            // Agregar lógica de negocio sobre el evento
            log.info("Procesando evento ...........");
            
        } catch (Exception e) {

            log.error("Error en el procesando del evento en el flujo de negocio: ", e);
            throw new RuntimeException("Error en el procesamiento del evento: ", e);
        }
    }
}