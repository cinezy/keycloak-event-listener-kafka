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
  private final boolean enableUserEvents;
  private final boolean enableAdminEvents;

  public KafkaEventListenerProvider(
      KeycloakSession session,
      String topicUser,
      String topicAdmin,
      boolean sync,
      KafkaProducerHolder.Config config,
      boolean enableUserEvents,
      boolean enableAdminEvents) {
    this.session = session;
    this.topicUser = topicUser;
    this.topicAdmin = topicAdmin;
    this.sync = sync;
    this.enableUserEvents = enableUserEvents;
    this.enableAdminEvents = enableAdminEvents;

    KafkaProducerHolder.initIfNeeded(config);
  }

  @Override
  public void onEvent(Event event) {
    if (!enableUserEvents) return;
    try {
      UserEventPayload payload = UserEventPayload.from(event, session);
      String json = mapper.writeValueAsString(payload);
      String key = payload.key();
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
    if (!enableAdminEvents) return;
    try {
      AdminEventPayload payload =
          AdminEventPayload.from(adminEvent, session, includeRepresentation);
      String json = mapper.writeValueAsString(payload);
      String key = payload.key();
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
