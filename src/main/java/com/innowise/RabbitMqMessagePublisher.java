package com.innowise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class RabbitMqMessagePublisher implements MessagePublisher {
    private static final Logger log = Logger.getLogger(RabbitMqMessagePublisher.class);

    private final Connection connection;
    private final RabbitMqConfig config;
    private final ObjectMapper objectMapper;

    public RabbitMqMessagePublisher(Connection connection, RabbitMqConfig config, ObjectMapper objectMapper) {
        this.connection = connection;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishEvent(String action, String realmId, String targetId, Object payload) {
        if (connection == null || !connection.isOpen()) {
            log.error("RabbitMQ connection not available");
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

            try (Channel channel = connection.createChannel()) {
                channel.basicPublish(config.getExchange(), routingKey, null,
                        json.getBytes(StandardCharsets.UTF_8));
                log.infof("Successfully published to RabbitMQ: %s", routingKey);
            }
        } catch (Exception e) {
            log.error("Failed to publish event to RabbitMQ", e);
        }
    }
}
