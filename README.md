# Kafka Listener Microservice

Microservicio para consumir y procesar eventos de un servidor Kafka desarrollado con Spring Boot 3.x, Java 17 y Maven con 1. **Clonar el proyecto**:

```bash
cd C:\Documents\services
git clone <repository-url> kafka-listener
cd kafka-l### Desarrollo

# Ejecutar con perfil de desarrollo
mvn spring-boo### Errores de conexión
- Verifica la configuración de `kafka.server`
- Confirma que el topic configurado en `kafka.consumer.topic` existe
- Revisa la configuración de red y firewall
- Consulta los logs del `KafkaHealthService` para detalles de conectividad

### Errores de procesamiento
- Revisa las validaciones en `ValidateEventService`
- Verifica el formato JSON en `KafkaTransform`
- Consulta los logs de `ProcessEventService` para errores de negocion -Dspring-boot.run.profiles=dev

# Ejecutar con logging debug
mvn spring-boot:run -Dlogging.level.com.pinncode.service.kafkalistener=DEBUG

# Compilar sin tests
mvn clean compile -DskipTests
```

### Producción
```bash
# Ejecutar JAR con perfil de producción
java -jar -Dspring.profiles.active=prod target/kafka-listener-1.0.0.jar

# Verificar health
curl http://localhost:8080/actuator/health
# Compilar el proyecto:
mvn clean compile
```

3. **Ejecutar tests**:
   ```bash
   mvn test
   # Ejecutar la aplicación:
   mvn spring-boot:run
   ```

5. **Generar JAR ejecutable**:
```bash
   mvn clean package
   java -jar target/kafka-listener-1.0.0.jar
```

##  Características Principales

-  **Consumo de eventos Kafka** con procesamiento robusto y acknowledgment manual
-  **Reconexión automática** cada minuto cuando Kafka no está disponible
-  **Reintentos configurables** con backoff exponencial
-  **Health checks automáticos** para monitorear la conexión con Spring Boot Actuator
-  **Manejo de errores** diferenciado con excepciones no reintentables
-  **Validación de eventos** con Bean Validation (JSR-303)
-  **Transformación JSON** a objetos de dominio con Jackson
-  **Configuración YAML** flexible por ambiente
-  **Métricas y monitoreo** con Actuator
-  **Logging detallado** para debugging y troubleshooting

##  Tecnologías Utilizadas

- **Java 17**
- **Spring Boot 3.2.8**
- **Spring Kafka 3.1.7**
- **Spring Retry** - Para reintentos automáticos
- **Maven** - Gestión de dependencias
- **Lombok** - Reducción de código boilerplate
- **Jackson** - Serialización/deserialización JSON con soporte Java Time
- **Jakarta Validation** - Validación de beans
- **Spring Boot Actuator** - Métricas y health checks

##  Estructura del Proyecto

```
kafka-listener/
├── src/
│   ├── main/
│   │   ├── java/com/pinncode/service/kafkalistener/
│   │   │   ├── KafkaMdpApplication.java          # Clase principal
│   │   │   ├── config/
│   │   │   │   └── KafkaConfig.java              # Configuración Kafka con reintentos
│   │   │   ├── exception/
│   │   │   │   └── KafkaException.java           # Excepción específica de Kafka
│   │   │   ├── model/
│   │   │   │   ├── KafkaMessage.java             # Modelo de mensaje (deprecado)
│   │   │   │   └── KafkaEvent.java               # Modelo principal de evento
│   │   │   ├── service/
│   │   │   │   ├── IKafkaListenerService.java    # Interfaz del listener
│   │   │   │   ├── KafkaListenerService.java     # Listener principal con @KafkaListener
│   │   │   │   ├── IKafkaMessageService.java     # Interfaz del servicio de mensajes
│   │   │   │   ├── KafkaMessageService.java      # Servicio de mensajes
│   │   │   │   ├── KafkaHealthService.java       # Health checks de Kafka
│   │   │   │   ├── IProcessEventService.java     # Interfaz de procesamiento
│   │   │   │   ├── ProcessEventService.java      # Procesamiento de eventos
│   │   │   │   ├── IValidateEventService.java    # Interfaz de validación
│   │   │   │   └── ValidateEventService.java     # Validación con Bean Validation
│   │   │   └── transform/
│   │   │       └── KafkaTransform.java           # Transformación JSON a objetos
│   │   └── resources/
│   │       └── application.yml                   # Configuración con reintentos
│   └── test/
│       └── java/com/pinncode/service/kafkalistener/
│           ├── KafkaMessageServiceTest.java
│           └── test/
│               └── KafkaConnectionTest.java
├── pom.xml                                       # Dependencias Maven
└── README.md                                     # Este archivo
```

## Arquitectura y Flujo de Procesamiento

### Componentes Principales

1. **KafkaListenerService**: Listener principal que consume eventos del tópico configurado
2. **KafkaTransform**: Transforma payloads JSON a objetos `KafkaEvent`
3. **ValidateEventService**: Valida eventos usando Bean Validation (JSR-303)
4. **ProcessEventService**: Ejecuta la lógica de negocio sobre eventos válidos
5. **KafkaHealthService**: Monitorea la conectividad con Kafka
6. **KafkaConfig**: Configuración centralizada de consumidores, contenedores y manejo de errores

### Flujo de Procesamiento

```
Kafka Topic → KafkaListenerService → KafkaTransform → ValidateEventService → ProcessEventService
                     ↓
             KafkaHealthService (verifica conectividad)
                     ↓
             Acknowledgment (confirma procesamiento)
```

##  Configuración de Reintentos

### Configuración en application.yml

```yaml
kafka:
  server: localhost:9092
  consumer:
    topic: visit-request-topic
    group-id: kafka-listener-consumer-group
    auto-offset-reset: earliest
    enable-auto-commit: false
    max-poll-records: 500
    session-timeout-ms: 30000
    heartbeat-interval-ms: 10000
  connection:
    retry:
      enabled: true
      max-attempts: 3              # Máximo de reintentos
      initial-interval: 60000      # 1 minuto inicial
      max-interval: 300000         # 5 minutos máximo
      multiplier: 1.5              # Incremento gradual
    timeout:
      connection-timeout: 30000
      request-timeout: 60000
    health-check:
      enabled: true
      interval: 60000              # Verificar cada minuto
      timeout: 30000
```

### Comportamiento de Reintentos

1. **Health Check Automático**: Verifica la conexión cada minuto usando `AdminClient.describeCluster()`
2. **Reintentos Configurables**: Máximo 3 intentos por defecto con backoff exponencial
3. **Manejo de Errores Diferenciado**: 
   - **KafkaException**: Se reintenta (problemas de conectividad)
   - **Excepciones de parsing/validación**: Se confirma para evitar loops infinitos
4. **Acknowledgment Manual**: Control explícito de offsets para garantizar procesamiento
5. **Logging Detallado**: Información completa sobre reintentos y estado de conexión

### Excepciones No Reintentables

- `IllegalArgumentException`
- `NullPointerException` 
- `JsonParseException`
- `JsonMappingException`

##  Instalación y Ejecución

### Prerrequisitos

- Java 17+
- Maven 3.8+
- Kafka Server (local o remoto)

### Pasos de Instalación

1. **Clonar el proyecto**:
```Bash
   cd C:Documents\\services
   git clone <repository-url> kafka-mdp
   cd kafka-mdp
```

2. **Compilar el proyecto**:
```Bash
   mvn clean compile
```

3. **Ejecutar tests**:
```Bash
   mvn test
```

4. **Ejecutar la aplicación**:
```Bash
   mvn spring-boot:run
```

5. **Generar JAR ejecutable**:
```Bash
   mvn clean package
   java -jar target/kafka-mdp-1.0.0.jar
```

##  Monitoreo y Health Checks

### Endpoints de Actuator

- **Health general**: GET http://localhost:8080/actuator/health

### Ejemplo de Health Check Response

```json
{
  "status": "UP",
  "components": {
    "kafkaHealthService": {
      "status": "UP",
      "details": {
        "kafka": "Connected",
        "lastCheck": "2025-08-15T17:47:46.124"
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

##  Configuración por Ambiente

### Perfil Local (por defecto)
- Reintentos cada 1 minuto, configurable
- Health check cada 1 minuto
- Logging en DEBUG

### Perfil Desarrollo
- Reintentos cada 30 segundos
- Máximo 2 minutos entre reintentos

### Perfil Producción
- Reintentos cada 2 minutos
- Máximo 10 minutos entre reintentos
- Health check cada 2 minutos
- Logging en INFO/WARN

##  Estructura de Eventos

El microservicio procesa eventos con la siguiente estructura JSON básica:

```json
{
  "reference": "REF000000001",
  "status": "In Progress",
  "created_datetime": "2023-10-01T12:00:00Z"
}
```

**Nota**: La estructura exacta del evento se define en la clase `KafkaEvent` y puede variar según los requisitos de negocio.

##  Logging y Debugging

### Logs de Conexión

-  **Conexión establecida**: "Conexión Kafka establecida exitosamente"
-  **Conexión perdida, reintento**: "Error de conexión Kafka, intentando reconectar: {error}"
-  **Programando reintento**: "Programando reintento de conexión con Kafka..."

### Logs de Eventos

-  **Evento recibido**: "------------- Evento kafka recibido -------------"
-  **Procesado exitoso**: "Mensaje/evento kafka procesado"
-  **Error de conexión**: "Error de conexión kafka: {error}"
-  **Error inesperado**: "Error inesperado durante el procesamiento del evento: {error}"
-  **Payload vacío**: "Mensaje/evento vacío, confirmación de lectura, se omite procesamiento de evento"
-  **Error de transformación**: "Error al transformar el payload del evento"

##  Comandos Útiles

### Desarrollo
```bash
# Ejecutar con perfil de desarrollo
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Ejecutar con logging debug
mvn spring-boot:run -Dlogging.level.com.kafka.mdp=DEBUG

# Compilar sin tests
mvn clean compile -DskipTests
```

### Producción
```bash
# Ejecutar JAR con perfil de producción
java -jar -Dspring.profiles.active=prod target/kafka-mdp-1.0.0.jar

# Verificar health
curl http://localhost:8080/actuator/health
```

## Validación de Eventos

El microservicio utiliza **Bean Validation (JSR-303)** para validar automáticamente los eventos recibidos. Las validaciones se definen mediante anotaciones en la clase `KafkaEvent`:

```java
@NotNull
@NotBlank
private String eventType;

@Past
private LocalDateTime timestamp;
```

Si un evento no pasa las validaciones, se registra el error y se confirma el mensaje para evitar reprocessamiento.

##  Configuración de Seguridad

Para entornos de producción, considera configurar:

- Autenticación SASL/SSL
- Encriptación de datos en tránsito
- Configuración de ACLs en Kafka
- Secrets management para credenciales

##  Solución de Problemas

### Kafka no disponible
- El microservicio reintentará automáticamente cada minuto
- Verifica los logs para información detallada
- Usa /actuator/health para verificar el estado

### Errores de conexión
- Verifica la configuración de 'server'
- Confirma que el topic existe
- Revisa la configuración de red y firewall

### Memoria y rendimiento
- Ajusta \max-poll-records\ según tu carga
- Considera ajustar timeouts según latencia de red