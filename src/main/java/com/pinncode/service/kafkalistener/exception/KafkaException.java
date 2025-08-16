package com.pinncode.service.kafkalistener.exception;

import java.io.Serial;

/**
 * Excepción de tiempo de ejecución específica de la aplicación para representar
 * errores relacionados con operaciones de Kafka (consumo, deserialización,
 * conexión, administración, etc.).
 * <p>
 * Esta excepción es unchecked (extiende {@link RuntimeException}), por lo que
 * puede propagarse sin declaración explícita en las firmas de métodos. Úsela
 * para encapsular y dar contexto a errores bajos de nivel (por ejemplo, de la
 * librería de Kafka o del cliente de administración) y permitir un manejo
 * centralizado en capas superiores.
 * <p>
 * Casos de uso típicos:
 * <ul>
 *   <li>Errores de deserialización o formato de mensaje.</li>
 *   <li>Indisponibilidad de broker o timeouts de conexión/solicitud.</li>
 *   <li>Fallos al administrar tópicos vía {@code AdminClient}.</li>
 *   <li>Errores de negocio detectados durante el procesamiento que requieren corte del flujo.</li>
 * </ul>
 */
public class KafkaException extends RuntimeException {

    /**
     * Serial version UID para compatibilidad de serialización.
     */
    @Serial
    private static final long serialVersionUID = -3270634603234977392L;

    /**
      * Crea una nueva excepción con un mensaje descriptivo.
      *
      * @param message descripción clara del error ocurrido; se recomienda incluir
      *                información contextual (tópico, partición, offset, key, etc.)
      */
    public KafkaException(String message) {
        super(message);
    }

     /**
      * Crea una nueva excepción con un mensaje y una causa raíz.
      *
      * @param message descripción clara del error ocurrido; se recomienda incluir
      *                información contextual (tópico, partición, offset, key, etc.)
      * @param cause   excepción original que provocó el fallo (puede ser {@code null})
      */
    public KafkaException(String message, Throwable cause) {
        super(message, cause);
    }
}
