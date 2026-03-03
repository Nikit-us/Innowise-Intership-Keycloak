package com.innowise;

import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.HashMap;
import java.util.Map;

public class UserService {
    private static final Logger log = Logger.getLogger(UserService.class);
    
    private final KeycloakSession session;

    public UserService(KeycloakSession session) {
        this.session = session;
    }

    public Map<String, Object> fetchUserAsDto(String userId) {
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
}
