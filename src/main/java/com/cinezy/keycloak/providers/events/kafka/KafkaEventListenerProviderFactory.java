package com.cinezy.keycloak.providers.events.kafka;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class KafkaEventListenerProviderFactory implements EventListenerProviderFactory {
  private static final Logger log =
      Logger.getLogger(KafkaEventListenerProviderFactory.class.getName());
  public static final String ID = "kafka";

  private String topicUser;
  private String topicAdmin;
  private boolean sync;
  private boolean enableUserEvents;
  private boolean enableAdminEvents;

  // Full Kafka config packed vào holder
  private KafkaProducerHolder.Config cfg;

  @Override
  public EventListenerProvider create(KeycloakSession session) {
    return new KafkaEventListenerProvider(
        session, topicUser, topicAdmin, sync, cfg, enableUserEvents, enableAdminEvents);
  }

  @Override
  public void init(Config.Scope root) {
    topicUser = root.get("topic-user", "keycloak.user.events");
    topicAdmin = root.get("topic-admin", "keycloak.admin.events");
    sync = root.getBoolean("sync", false);

    // new flags
    enableUserEvents = root.getBoolean("enable-user-events", true);
    enableAdminEvents = root.getBoolean("enable-admin-events", true);

    // === Build Kafka config ===
    KafkaProducerHolder.Config c = new KafkaProducerHolder.Config();
    c.bootstrapServers = root.get("bootstrap-servers", "localhost:9092");
    c.clientId = root.get("client-id", "keycloak");
    c.acks = root.get("producer.acks", "all");
    c.retries = root.getInt("producer.retries", 3);
    c.lingerMs = root.getInt("producer.linger-ms", 5);
    c.batchSize = root.getInt("producer.batch-size", 16384);
    c.compressionType = root.get("producer.compression-type", null);
    c.deliveryTimeoutMs = root.getInt("producer.delivery-timeout-ms", 120_000);
    c.requestTimeoutMs = root.getInt("producer.request-timeout-ms", 30_000);
    c.enableIdempotence = root.getBoolean("producer.enable-idempotence", true);
    int defaultMif = c.enableIdempotence ? 5 : 1;
    c.maxInFlightRequestsPerConnection =
        root.getInt("producer.max-in-flight-requests-per-connection", defaultMif);

    c.securityProtocol = root.get("security.protocol", null);
    c.saslMechanism = root.get("sasl.mechanism", null);
    c.saslJaasConfig = root.get("sasl.jaas-config", null);
    c.sslTruststoreLocation = root.get("ssl.truststore.location", null);
    c.sslTruststorePassword = root.get("ssl.truststore.password", null);
    c.sslKeystoreLocation = root.get("ssl.keystore.location", null);
    c.sslKeystorePassword = root.get("ssl.keystore.password", null);
    c.sslKeyPassword = root.get("ssl.key.password", null);

    // props.*
    c.extraProps = collectExtraProps(root, "props.");

    this.cfg = c;
    log.info("KafkaEventListenerProviderFactory initialized");
  }

  @Override
  public void postInit(KeycloakSessionFactory keycloakSessionFactory) {}

  @Override
  public void close() {}

  @Override
  public String getId() {
    return ID;
  }

  private static Properties collectExtraProps(Config.Scope root, String prefix) {
    Properties extra = new Properties();

    // Env format: KC_SPI_EVENTS_LISTENER_KAFKA_PROPS_FOO_BAR=123  -> props.foo.bar=123
    String envPrefix = "KC_SPI_EVENTS_LISTENER_" + ID.toUpperCase(Locale.ROOT) + "_PROPS_";
    for (Map.Entry<String, String> e : System.getenv().entrySet()) {
      String key = e.getKey();
      if (key.startsWith(envPrefix)) {
        String tail =
            key.substring(envPrefix.length()) // FOO_BAR
                .toLowerCase(Locale.ROOT)
                .replace("__", "@@") // allow escaping with double underscore
                .replace('_', '.') // FOO_BAR -> foo.bar
                .replace("@@", "_");
        extra.put(tail, e.getValue());
      }
    }

    // Also check system properties if users set -Dspi-events-listener-kafka-props.x=y
    String sysPrefix = "spi-events-listener-" + ID + "-props.";
    for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
      String k = String.valueOf(e.getKey());
      if (k.startsWith(sysPrefix)) {
        extra.put(k.substring(sysPrefix.length()), String.valueOf(e.getValue()));
      }
    }

    return extra;
  }
}
