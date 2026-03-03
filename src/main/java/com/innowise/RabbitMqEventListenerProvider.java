package com.innowise;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.util.Map;
import java.util.Set;

public class RabbitMqEventListenerProvider implements EventListenerProvider {

    private final MessagePublisher publisher;
    private final UserService userService;

    private static final Set<OperationType> USER_OPERATIONS = Set.of(
            OperationType.CREATE,
            OperationType.UPDATE,
            OperationType.DELETE);

    public RabbitMqEventListenerProvider(MessagePublisher publisher, UserService userService) {
        this.publisher = publisher;
        this.userService = userService;
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType() == EventType.REGISTER) {
            Map<String, Object> userDto = userService.fetchUserAsDto(event.getUserId());
            if (userDto != null) {
                publisher.publishEvent("USER_CREATE", event.getRealmId(), event.getUserId(), userDto);
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
            publisher.publishEvent(action, adminEvent.getAuthDetails().getRealmId(), userId, Map.of("id", userId));
        } else {
            Map<String, Object> userDto = userService.fetchUserAsDto(userId);
            if (userDto != null) {
                publisher.publishEvent(action, adminEvent.getAuthDetails().getRealmId(), userId, userDto);
            }
        }
    }

    @Override
    public void close() {
    }
}