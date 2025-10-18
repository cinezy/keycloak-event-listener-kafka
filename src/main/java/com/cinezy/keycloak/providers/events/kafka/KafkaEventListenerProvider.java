package com.cinezy.keycloak.providers.events.kafka;

import com.cinezy.keycloak.providers.events.kafka.model.AdminEventPayload;
import com.cinezy.keycloak.providers.events.kafka.model.UserEventPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.util.JsonSerialization;

public class KafkaEventListenerProvider implements EventListenerProvider {
  private static final Logger log = Logger.getLogger(KafkaEventListenerProvider.class.getName());

  private final KeycloakSession session;
  private final ObjectMapper mapper = JsonSerialization.mapper;

  private final String topicUser;
  private final String topicAdmin;
  private final boolean sync;

  public KafkaEventListenerProvider(
      KeycloakSession session,
      String bootstrapServers,
      String topicUser,
      String topicAdmin,
      String acks,
      boolean sync,
      Integer lingerMs,
      Integer batchSize,
      Integer retries,
      String securityProtocol,
      String saslMechanism,
      String saslJaasConfig) {
    this.session = session;
    this.topicUser = topicUser;
    this.topicAdmin = topicAdmin;
    this.sync = sync;

    KafkaProducerHolder.initIfNeeded(
        bootstrapServers,
        acks,
        lingerMs,
        batchSize,
        retries,
        securityProtocol,
        saslMechanism,
        saslJaasConfig);
  }

  @Override
  public void onEvent(Event event) {
    try {
      var payload = UserEventPayload.from(event, session);
      var json = mapper.writeValueAsString(payload);
      var key = payload.key();
      if (sync) {
        KafkaProducerHolder.sendSync(topicUser, key, json);
      } else {
        KafkaProducerHolder.sendAsync(topicUser, key, json);
      }
    } catch (Exception e) {
      // Keep Keycloak healthy; just log
      log.errorf(e, "Failed to publish user event to Kafka");
    }
  }

  @Override
  public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
    try {
      var payload = AdminEventPayload.from(adminEvent, session, includeRepresentation);
      var json = mapper.writeValueAsString(payload);
      var key = payload.key();
      if (sync) {
        KafkaProducerHolder.sendSync(topicAdmin, key, json);
      } else {
        KafkaProducerHolder.sendAsync(topicAdmin, key, json);
      }
    } catch (Exception e) {
      // Keep Keycloak healthy; just log
      log.errorf(e, "Failed to publish admin event to Kafka");
    }
  }

  @Override
  public void close() {}
}
