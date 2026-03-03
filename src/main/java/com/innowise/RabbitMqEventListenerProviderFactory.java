package com.innowise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class RabbitMqEventListenerProviderFactory implements EventListenerProviderFactory {
    private static final Logger log = Logger.getLogger(RabbitMqEventListenerProviderFactory.class);

    private RabbitMqConfig config;
    private Connection connection;
    private Channel channel;
    private ObjectMapper objectMapper;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        initConnection();
        MessagePublisher publisher = new RabbitMqMessagePublisher(connection, config, objectMapper);
        UserService userService = new UserService(session);
        return new RabbitMqEventListenerProvider(publisher, userService);
    }

    private synchronized void initConnection() {
        if (connection != null && connection.isOpen() && channel != null && channel.isOpen()) {
            return;
        }

        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(config.getHost());
            factory.setPort(config.getPort());
            factory.setUsername(config.getUsername());
            factory.setPassword(config.getPassword());
            factory.setVirtualHost(config.getVirtualHost());

            this.connection = factory.newConnection();
            this.channel = connection.createChannel();

            channel.exchangeDeclare(config.getExchange(), "topic", true);

            log.info("RabbitMQ connection established in Factory");
        } catch (Exception e) {
            log.error("Failed to connect to RabbitMQ in Factory", e);
        }
    }

    @Override
    public void init(Config.Scope config) {
        this.config = new RabbitMqConfig();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
            log.info("RabbitMQ connection closed in Factory");
        } catch (Exception e) {
            log.error("Error closing RabbitMQ connection in Factory", e);
        }
    }

    @Override
    public String getId() {
        return "rabbitmq-event-listener";
    }
}