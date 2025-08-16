package com.pinncode.service.kafkalistener.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.sql.Timestamp;

/**
 * Modelo principal que representa un mensaje/evento de Kafka completo.
 */
@ToString
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KafkaEvent {

    /**
     * Referencia única de algún evento o transacción asociada al mensaje.
     */
    @NotNull(message = "La referencia no puede ser nula")
    @JsonProperty("reference")
    private String reference;

    /**
     * Estatus del proceso asociado al mensaje/evento.
     */
    @JsonProperty("status")
    private String status;

    /**
     * Fecha y hora de creación del mensaje/evento.
     */
    @JsonProperty("created_datetime")
    private Timestamp createdDateTime;
}