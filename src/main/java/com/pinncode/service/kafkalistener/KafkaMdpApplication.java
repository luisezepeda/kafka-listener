package com.pinncode.service.kafkalistener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import lombok.extern.slf4j.Slf4j;

/**
 * Clase principal del microservicio Kafka Listener.
 * Microservicio para leer y procesar eventos Kafka.
 */
@Slf4j
@SpringBootApplication
public class KafkaMdpApplication {

    /**
     * Método principal para iniciar la aplicación Spring Boot.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(KafkaMdpApplication.class, args);
    }
}