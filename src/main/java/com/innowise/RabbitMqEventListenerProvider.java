package com.innowise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RabbitMqEventListenerProvider implements EventListenerProvider {

    private static final Logger log = Logger.getLogger(RabbitMqEventListenerProvider.class);

    private final RabbitMqConfig config;
    private final ObjectMapper objectMapper;
    private final Channel channel;
    private final KeycloakSession session;

    private static final Set<OperationType> USER_OPERATIONS = Set.of(
            OperationType.CREATE,
            OperationType.UPDATE,
            OperationType.DELETE);

    public RabbitMqEventListenerProvider(RabbitMqConfig config, Channel channel, KeycloakSession session) {
        this.config = config;
        this.channel = channel;
        this.session = session;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType() == EventType.REGISTER) {
            Map<String, Object> userDto = fetchUserAsDto(event.getUserId());
            if (userDto != null) {
                publishEvent("USER_CREATE", event.getRealmId(), event.getUserId(), userDto);
            }
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        if (adminEvent.getResourceType() != ResourceType.USER
                || !USER_OPERATIONS.contains(adminEvent.getOperationType())) {
            return;
        }

        String resourcePath = adminEvent.getResourcePath();
        String userId = resourcePath.startsWith("users/") ? resourcePath.substring(6) : resourcePath;
        String action = "USER_" + adminEvent.getOperationType().name();

        if (adminEvent.getOperationType() == OperationType.DELETE) {
            publishEvent(action, adminEvent.getAuthDetails().getRealmId(), userId, Map.of("id", userId));
        } else {
            Map<String, Object> userDto = fetchUserAsDto(userId);
            if (userDto != null) {
                publishEvent(action, adminEvent.getAuthDetails().getRealmId(), userId, userDto);
            }
        }
    }

    private Map<String, Object> fetchUserAsDto(String userId) {
        try {
            RealmModel realm = session.getContext().getRealm();
            UserModel user = session.users().getUserById(realm, userId);

            if (user == null) {
                log.warnf("User not found in Keycloak: %s", userId);
                return null;
            }

            Map<String, Object> dto = new HashMap<>();
            dto.put("name", user.getFirstName());
            dto.put("surname", user.getLastName());
            dto.put("email", user.getEmail());

            String birthDate = user.getFirstAttribute("birthDate");
            dto.put("birthDate", birthDate);

            return dto;
        } catch (Exception e) {
            log.error("Failed to fetch user data for DTO", e);
            return null;
        }
    }

    private void publishEvent(String action, String realmId, String targetId, Object payload) {
        if (channel == null || !channel.isOpen()) {
            log.error("RabbitMQ channel not available");
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("action", action);
            message.put("realmId", realmId);
            message.put("userId", targetId);
            message.put("payload", payload);
            message.put("timestamp", System.currentTimeMillis());

            String json = objectMapper.writeValueAsString(message);
            String routingKey = "keycloak.user." + action.replace("USER_", "").toLowerCase();

            channel.basicPublish(config.getExchange(), routingKey, null,
                    json.getBytes(StandardCharsets.UTF_8));

            log.infof("Successfully published to RabbitMQ: %s", routingKey);
        } catch (Exception e) {
            log.error("Failed to publish event to RabbitMQ", e);
        }
    }

    @Override
    public void close() {
    }
}