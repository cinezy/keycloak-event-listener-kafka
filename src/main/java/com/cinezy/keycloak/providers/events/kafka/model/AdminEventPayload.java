package com.cinezy.keycloak.providers.events.kafka.model;

import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

public record AdminEventPayload(
    String realmId,
    String realmName,
    String resourceType,
    String operationType,
    String resourcePath,
    Long time,
    String authRealmId,
    String authClientId,
    String authUserId,
    String authIpAddress,
    String representation // optional JSON
    ) {
  public static AdminEventPayload from(
      AdminEvent e, KeycloakSession session, boolean includeRepresentation) {
    RealmModel realm = session.realms().getRealm(e.getRealmId());
    return new AdminEventPayload(
        e.getRealmId(),
        realm != null ? realm.getName() : null,
        e.getResourceType() != null ? e.getResourceType().name() : null,
        e.getOperationType() != null ? e.getOperationType().name() : null,
        e.getResourcePath(),
        e.getTime(),
        e.getAuthDetails() != null ? e.getAuthDetails().getRealmId() : null,
        e.getAuthDetails() != null ? e.getAuthDetails().getClientId() : null,
        e.getAuthDetails() != null ? e.getAuthDetails().getUserId() : null,
        e.getAuthDetails() != null ? e.getAuthDetails().getIpAddress() : null,
        includeRepresentation ? e.getRepresentation() : null);
  }

  public String key() {
    return (realmName != null ? realmName : realmId) + ":" + operationType + ":" + resourceType;
  }
}
