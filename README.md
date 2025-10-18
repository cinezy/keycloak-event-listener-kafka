# 🦊 keycloak-event-listener-kafka

A **Keycloak Event Listener SPI** that publishes **user and admin events** to **Apache Kafka** — designed for Keycloak *
*v26+**.  
This plugin provides **Spring Boot–style configuration**, async/sync publishing modes, and fine-grained control over
which events to emit.

---

## 🚀 Features

- ✅ Publishes **user events** (login, register, logout, etc.)
- ✅ Publishes **admin events** (realm, user, client, role changes, etc.)
- ✅ Configurable via **flat “Spring Boot–like” properties**
- ✅ Supports **SASL/SSL**, custom producer tuning, compression, retries, and idempotence
- ✅ Supports **async or sync** delivery mode
- ✅ Toggle user/admin event emission independently
- ✅ Works with **Keycloak 26+**
- ✅ Packaged with **maven-shade-plugin** (drop-in JAR under `/opt/keycloak/providers/`)

---

## 🧱 Build

```bash
# Prerequisites
Java 21+
Maven 3.8+
Keycloak 26.x

# Build the shaded provider JAR
mvn clean package -DskipTests
```

Output:

```
target/keycloak-event-listener-kafka-1.0.0.jar
```

---

## 🧩 Installation (Docker / Helm)

### Dockerfile

```dockerfile
FROM quay.io/keycloak/keycloak:26.0.0
WORKDIR /opt/keycloak

# Copy the provider JAR
COPY target/keycloak-event-listener-kafka-1.0.0.jar /opt/keycloak/providers/

# Optional: build optimized config
RUN /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--http-enabled=true", "--hostname-strict=false"]
```

### Helm values snippet

```yaml
extraEnv:
  - name: KC_SPI_EVENTS_LISTENER_KAFKA_BOOTSTRAP_SERVERS
    value: "kafka:9092"
  - name: KC_SPI_EVENTS_LISTENER_KAFKA_TOPIC_USER
    value: "keycloak.user.events"
  - name: KC_SPI_EVENTS_LISTENER_KAFKA_TOPIC_ADMIN
    value: "keycloak.admin.events"
  - name: KC_SPI_EVENTS_LISTENER_KAFKA_ENABLE_USER_EVENTS
    value: "true"
  - name: KC_SPI_EVENTS_LISTENER_KAFKA_ENABLE_ADMIN_EVENTS
    value: "false"
```

---

### Test with Docker compose
```bash
mvn clean package -DskipTests

mkdir -p providers
# copy your jar into ./providers/
cp target/keycloak-event-listener-kafka-*.jar ./providers/

docker-compose up -d

```

## ⚙️ Configuration

All configuration lives under one **flat scope** (`spi-events-listener-kafka-*`),  
with key names similar to **Spring Kafka**.

| Category        | Property                                         | Example                                                                                              | Description                    |
|-----------------|--------------------------------------------------|------------------------------------------------------------------------------------------------------|--------------------------------|
| **Core**        | `bootstrap-servers`                              | `broker:9092`                                                                                        | Kafka bootstrap list           |
|                 | `client-id`                                      | `keycloak-cinezy`                                                                                    | Producer client ID             |
| **Producer**    | `producer.acks`                                  | `all`                                                                                                | Acks mode                      |
|                 | `producer.retries`                               | `5`                                                                                                  | Retry count                    |
|                 | `producer.linger-ms`                             | `20`                                                                                                 | Batch linger                   |
|                 | `producer.batch-size`                            | `32768`                                                                                              | Batch bytes                    |
|                 | `producer.compression-type`                      | `snappy`                                                                                             | Compression                    |
|                 | `producer.enable-idempotence`                    | `true`                                                                                               | Exactly-once guarantee         |
|                 | `producer.max-in-flight-requests-per-connection` | `5`                                                                                                  | Ordering control               |
| **Security**    | `security.protocol`                              | `SASL_SSL`                                                                                           | Connection protocol            |
| **SASL**        | `sasl.mechanism`                                 | `PLAIN`                                                                                              | Auth mechanism                 |
|                 | `sasl.jaas-config`                               | `org.apache.kafka.common.security.plain.PlainLoginModule required username="KEY" password="SECRET";` | SASL JAAS config               |
| **SSL**         | `ssl.truststore.location`                        | `/opt/keycloak/certs/truststore.jks`                                                                 | Truststore path                |
|                 | `ssl.truststore.password`                        | `changeit`                                                                                           | Truststore password            |
| **Events**      | `enable-user-events`                             | `true`                                                                                               | Emit user events               |
|                 | `enable-admin-events`                            | `true`                                                                                               | Emit admin events              |
|                 | `topic-user`                                     | `keycloak.user.events`                                                                               | User topic                     |
|                 | `topic-admin`                                    | `keycloak.admin.events`                                                                              | Admin topic                    |
|                 | `sync`                                           | `false`                                                                                              | Blocking publish mode          |
| **Extra props** | `props.*`                                        | `props.metadata.max.age.ms=180000`                                                                   | Pass-through producer settings |

Example `keycloak.conf`:

```properties
spi-events-listener-kafka-bootstrap-servers=broker:9092
spi-events-listener-kafka-client-id=keycloak-cinezy
spi-events-listener-kafka-producer.acks=all
spi-events-listener-kafka-producer.linger-ms=10
spi-events-listener-kafka-producer.enable-idempotence=true
spi-events-listener-kafka-topic-user=keycloak.user.events
spi-events-listener-kafka-topic-admin=keycloak.admin.events
spi-events-listener-kafka-enable-user-events=true
spi-events-listener-kafka-enable-admin-events=false
spi-events-listener-kafka-sync=false
```

Or with environment variables:

```bash
KC_SPI_EVENTS_LISTENER_KAFKA_KAFKA_BOOTSTRAP_SERVERS=broker:9092
KC_SPI_EVENTS_LISTENER_KAFKA_PRODUCER_ACKS=all
KC_SPI_EVENTS_LISTENER_KAFKA_TOPIC_USER=keycloak.user.events
KC_SPI_EVENTS_LISTENER_KAFKA_ENABLE_USER_EVENTS=true
KC_SPI_EVENTS_LISTENER_KAFKA_ENABLE_ADMIN_EVENTS=false
```

---

## 🧠 How It Works

1. The provider implements Keycloak’s **EventListener SPI**.
2. For each event:
    - `UserEventPayload` or `AdminEventPayload` is serialized to JSON.
    - The message is published to Kafka (`async` by default).
3. Configuration is resolved from Keycloak’s SPI config system.

You can confirm activation in logs (debug level)

```
Sending event to Kafka: topic=keycloak.user.events, key=null, value={"type":"LOGIN","realmId":"cinezy","clientId":"admin-cli","userId":"0815f7c5-729d-410c-879b-029097011071","ipAddress":"127.0.0.1","createdTimestamp":1690999999999}
```

---

## 🔍 Enable the Listener in Keycloak

1. Open the **Admin Console** → select your realm (`cinezy`)
2. Go to **Realm Settings → Events**
3. Under **Event Listeners**, add:
   ```
   kafka
   ```
4. Enable *Save Events* and *Admin Events* if needed.

---

## 🧪 Local Testing (Redpanda / Kafka)

```bash
docker run -d --name redpanda -p 9092:9092   docker.redpanda.com/redpanda/redpanda:latest   redpanda start --overprovisioned --smp 1 --memory 512M --reserve-memory 0M --node-id 0   --kafka-addr PLAINTEXT://0.0.0.0:9092 --advertise-kafka-addr PLAINTEXT://localhost:9092
```

Consume messages:

```bash
docker run -it --rm --network=host edenhill/kafkacat:1.6.0   kafkacat -b localhost:9092 -t keycloak.user.events -C
```

---

## 🛡️ Safety Notes

- Keep `sync=false` to avoid blocking login flows.
- Use `acks=all` and `enable-idempotence=true` for reliable delivery.
- Use `props.*` for advanced tuning (timeouts, linger, max.request.size, etc.).
- Ensure Kafka credentials & SSL files are securely mounted.

---

## 📦 License

MIT License © 2025 Cinezy / Nguyen Hai

---

## 💡 Example Use Cases

- Forwarding audit logs to Kafka for ELK / OpenSearch indexing
- Monitoring login/logout metrics in Prometheus
- Triggering business workflows from Keycloak user lifecycle events
- Integrating Keycloak identity events into a data pipeline

---

### 🔗 Related

- [Keycloak SPI Docs](https://www.keycloak.org/docs/latest/server_development/#_providers)
- [Apache Kafka Clients](https://kafka.apache.org/documentation/#producerconfigs)
