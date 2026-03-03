package com.innowise;

public interface MessagePublisher {
    void publishEvent(String action, String realmId, String targetId, Object payload);
}
