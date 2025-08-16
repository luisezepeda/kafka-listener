package com.pinncode.service.kafkalistener.service;

import com.pinncode.service.kafkalistener.model.KafkaEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Servicio de validación para eventos Kafka recibidos.
 * <p>
 * Utiliza Bean Validation (JSR-303) para verificar que los eventos
 * cumplan con las reglas de negocio definidas mediante anotaciones
 * en el modelo {@link KafkaEvent}.
 * <p>
 * Características:
 * <ul>
 *   <li>Validación automática usando {@link Validator}</li>
 *   <li>Logging detallado de violaciones encontradas</li>
 *   <li>Retorno booleano simple para decisiones de procesamiento</li>
 * </ul>
 */
@Slf4j
@Service
public class ValidateEventService implements IValidateEventService {

    /** Validador JSR-303 para verificar anotaciones de Bean Validation */
    @Autowired
    private Validator validator;

    /**
     * Válida un evento Kafka contra las reglas de negocio definidas.
     * <p>
     * Ejecuta validación completa del objeto usando Bean Validation:
     * <ul>
     *   <li>Aplica todas las anotaciones de validación del modelo</li>
     *   <li>Registra violaciones detalladas en logs (Propiedad, valor, error)</li>
     *   <li>Retorna {@code false} si hay alguna violación en la información</li>
     * </ul>
     * 
     * @param message evento a validar con datos de negocio
     * @return {@code true} si el evento es válido, {@code false} si hay violaciones
     * @see ConstraintViolation
     * @see Validator#validate(Object, Class[])
     */
    @Override
    public boolean checkBusinessRulesForTheEvent(KafkaEvent message) {

        Set<ConstraintViolation<KafkaEvent>> violations = validator.validate(message);

        if (!violations.isEmpty()) {

            log.warn("Violaciones de validación encontradas:");

            violations.forEach(violation -> log.warn("    - Propiedad: [{}], Valor: [{}], Error: {}",
                                                                                        violation.getPropertyPath(),
                                                                                        violation.getInvalidValue(),
                                                                                        violation.getMessage())
            );

            return false;
        }

        return true;
    }
}
