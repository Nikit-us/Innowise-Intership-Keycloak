package com.innowise;

public class RabbitMqConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String virtualHost;
    private final String exchange;

    public RabbitMqConfig() {
        this.host = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        this.port = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
        this.username = System.getenv().getOrDefault("RABBITMQ_USERNAME", "guest");
        this.password = System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest");
        this.virtualHost = System.getenv().getOrDefault("RABBITMQ_VHOST", "/");
        this.exchange = System.getenv().getOrDefault("RABBITMQ_EXCHANGE", "keycloak.events");
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public String getExchange() {
        return exchange;
    }
}