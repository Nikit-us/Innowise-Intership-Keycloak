package com.innowise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class RabbitMqMessagePublisher implements MessagePublisher {
    private static final Logger log = Logger.getLogger(RabbitMqMessagePublisher.class);

    private final Connection connection;
    private final RabbitMqConfig config;
    private final ObjectMapper objectMapper;

    private final ThreadLocal<Channel> channelThreadLocal;

    public RabbitMqMessagePublisher(Connection connection, RabbitMqConfig config, ObjectMapper objectMapper) {
        this.connection = connection;
        this.config = config;
        this.objectMapper = objectMapper;

        this.channelThreadLocal = ThreadLocal.withInitial(() -> {
            try {
                return createConfiguredChannel();
            } catch (Exception e) {
                log.error("Failed to create RabbitMQ channel", e);
                return null;
            }
        });
    }

    private Channel createConfiguredChannel() throws IOException {
        if (connection == null || !connection.isOpen()) {
            throw new IOException("RabbitMQ connection is closed");
        }
        Channel channel = connection.createChannel();
        channel.confirmSelect();
        return channel;
    }

    @Override
    public void publishEvent(String action, String realmId, String targetId, Object payload) {
        if (connection == null || !connection.isOpen()) {
            log.error("RabbitMQ connection not available. Cannot publish event.");
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

            Channel channel = channelThreadLocal.get();

            if (channel == null || !channel.isOpen()) {
                channelThreadLocal.remove();
                channel = channelThreadLocal.get();
                if (channel == null) return;
            }

            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .contentEncoding("UTF-8")
                    .deliveryMode(2)
                    .timestamp(new Date())
                    .appId("keycloak-publisher")
                    .build();

            channel.basicPublish(config.getExchange(), routingKey, properties, json.getBytes(StandardCharsets.UTF_8));

            if (channel.waitForConfirms(5000)) {
                log.debugf("Successfully published and confirmed by RabbitMQ: %s", routingKey);
            } else {
                log.warnf("Message published but NOT confirmed by RabbitMQ: %s", routingKey);
            }

        } catch (Exception e) {
            log.error("Failed to publish event to RabbitMQ", e);
            channelThreadLocal.remove();
        }
    }
}