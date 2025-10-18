package com.cinezy.keycloak.providers.events.kafka;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class KafkaEventListenerProviderFactory implements EventListenerProviderFactory {
  private static final Logger log =
      Logger.getLogger(KafkaEventListenerProviderFactory.class.getName());
  public static final String ID = "kafka";

  // Configurable via Keycloak config: --spi-events-listener-kafka-*
  private String bootstrapServers;
  private String topicUser;
  private String topicAdmin;
  private String acks;
  private boolean sync;
  private Integer lingerMs;
  private Integer batchSize;
  private Integer retries;
  private String securityProtocol; // PLAINTEXT / SSL / SASL_SSL
  private String saslMechanism; // PLAIN / SCRAM-SHA-256 / SCRAM-SHA-512
  private String
      saslJaasConfig; // "org.apache.kafka.common.security.plain.PlainLoginModule required

  // username='...' password='...';"

  @Override
  public EventListenerProvider create(KeycloakSession keycloakSession) {
    return new KafkaEventListenerProvider(
        keycloakSession,
        bootstrapServers,
        topicUser,
        topicAdmin,
        acks,
        sync,
        lingerMs,
        batchSize,
        retries,
        securityProtocol,
        saslMechanism,
        saslJaasConfig);
  }

  @Override
  public void init(Config.Scope config) {
    bootstrapServers = config.get("kafka-bootstrap-servers", "localhost:9092");
    topicUser = config.get("topic-user", "keycloak.user.events");
    topicAdmin = config.get("topic-admin", "keycloak.admin.events");
    acks = config.get("acks", "all");
    sync = config.getBoolean("sync", false); // default async
    lingerMs = config.getInt("linger-ms", 5);
    batchSize = config.getInt("batch-size", 16384);
    retries = config.getInt("retries", 3);

    securityProtocol = config.get("security-protocol", null);
    saslMechanism = config.get("sasl-mechanism", null);
    saslJaasConfig = config.get("sasl-jaas-config", null);
  }

  @Override
  public void postInit(KeycloakSessionFactory keycloakSessionFactory) {}

  @Override
  public void close() {}

  @Override
  public String getId() {
    return ID;
  }
}
