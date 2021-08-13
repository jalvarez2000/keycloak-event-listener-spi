package com.juganu.juganutoken;

import org.keycloak.authentication.actiontoken.DefaultActionToken;

public class JuganuCredentialsActionToken extends DefaultActionToken {

    public static final String TOKEN_TYPE = "reset-credentials";

    public JuganuCredentialsActionToken(String userId, int absoluteExpirationInSecs, String clientId) {
        super(userId, TOKEN_TYPE, absoluteExpirationInSecs, null, null);
        this.issuedFor = clientId;
    }

    private JuganuCredentialsActionToken() {
    }
}