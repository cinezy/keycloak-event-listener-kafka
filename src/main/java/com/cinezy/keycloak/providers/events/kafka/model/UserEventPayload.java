package com.cinezy.keycloak.providers.events.kafka.model;

import org.keycloak.events.Event;
import org.keycloak.models.KeycloakSession;

import java.util.Map;

public record UserEventPayload(
    String realmId,
    String realmName,
    String clientId,
    String userId,
    String ipAddress,
    String eventType,
    long time,
    Map<String, String> details) {
  public static UserEventPayload from(Event e, KeycloakSession session) {
    var realm = session.realms().getRealm(e.getRealmId());
    return new UserEventPayload(
        e.getRealmId(),
        realm != null ? realm.getName() : null,
        e.getClientId(),
        e.getUserId(),
        e.getIpAddress(),
        e.getType() != null ? e.getType().name() : null,
        e.getTime(),
        e.getDetails());
  }

  public String key() {
    return (realmName != null ? realmName : realmId)
        + ":"
        + eventType
        + ":"
        + (userId != null ? userId : "anon");
  }
}
