package com.coderdude.sampleeventlistenerprovider.provider;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import org.keycloak.email.DefaultEmailSenderProvider;
import org.keycloak.email.EmailException;

import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.Charset;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;


public class SampleEventListenerProvider implements EventListenerProvider {

    private final KeycloakSession session;

    public SampleEventListenerProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {

        System.out.println("Event Occurred:" + toString(event));
        System.out.println("Event: " + event.getType());
        if (EventType.UPDATE_PASSWORD.equals(event.getType())) {
            RealmModel realm = session.realms().getRealm(event.getRealmId());
            String template = readTemplate("password-change.html");
            UserModel user = session.users().getUserById(event.getUserId(), realm);
            template = replacePlaceholder(template,"$FIRST_NAME",user.getFirstName());
            template = replacePlaceholder(template,"$EMAIL",user.getEmail());
            sendMail(user,"PASSWORD CHANGED",template);
        }

    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean b) {
        System.out.println("Event Occurred:" + toString(adminEvent));
        String userId = "NO ID";

        if (ResourceType.USER.equals(adminEvent.getResourceType())
                && OperationType.CREATE.equals(adminEvent.getOperationType())) {
            for (String retval: adminEvent.getResourcePath().split("/")) {
                userId = retval;
            }


            System.out.println("User id" + ": " + userId);
            UserModel user = this.session.users().getUserById(userId, this.session.getContext().getRealm());
            System.out.println("User name" + ": " + user.getFirstName());

            try {
                String template = readTemplate("password-change.html");
                template = replacePlaceholder(template,"$FIRST_NAME",user.getFirstName());
                template = replacePlaceholder(template,"$EMAIL",user.getEmail());
                sendMail(user, "CREATE USER",template);
            }
            catch (Exception e) {
                System.out.println("Error reading template from resource file: " + e);
            };
        }

        System.out.println("-----------------------------------------------------------");
    }

    @Override
    public void close() {

    }

    private String readTemplate(String templateName) {
        InputStream in = getClass().getResourceAsStream("/templates/"+ templateName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        try {
            String line = reader.readLine();
            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = reader.readLine();
            }
            System.out.println("Email Template: " + ": " + sb.toString());
        }
        catch (Exception e) {
            System.out.println("Error reading template from resource file: " + e);
        };
        try {
            reader.close();
        } catch (Exception e) {
            System.out.println("Error closing template file." + e);
        }
        return sb.toString();
    }

    private String replacePlaceholder(String template, String placeholder, String value) {
        return template.replace(placeholder,value);
    }

    private void sendMail(UserModel user,String header, String htmlText) {
        try {
            DefaultEmailSenderProvider senderProvider = new DefaultEmailSenderProvider(this.session);
            senderProvider.send(
                    session.getContext().getRealm().getSmtpConfig(),
                    user,
                    header,
                    "",
                    htmlText
            );
        } catch (EmailException e) {
            System.out.println("Error sending email");
        }
    }

    private String toString(Event event) {

        StringBuilder sb = new StringBuilder();
        sb.append("type=");
        sb.append(event.getType());
        sb.append(", realmId=");
        sb.append(event.getRealmId());
        sb.append(", clientId=");
        sb.append(event.getClientId());
        sb.append(", userId=");
        sb.append(event.getUserId());
        sb.append(", ipAddress=");
        sb.append(event.getIpAddress());


        if (event.getError() != null) {
            sb.append(", error=");
            sb.append(event.getError());
        }

        if (event.getDetails() != null) {
            for (Map.Entry<String, String> e : event.getDetails().entrySet()) {
                sb.append(", ");
                sb.append(e.getKey());
                if (e.getValue() == null || e.getValue().indexOf(' ') == -1) {
                    sb.append("=");
                    sb.append(e.getValue());
                } else {
                    sb.append("='");
                    sb.append(e.getValue());
                    sb.append("'");
                }
            }
        }
        return sb.toString();

    }


    private String toString(AdminEvent adminEvent) {

        StringBuilder sb = new StringBuilder();
        sb.append("operationType=");
        sb.append(adminEvent.getOperationType());
        sb.append(", realmId=");
        sb.append(adminEvent.getAuthDetails().getRealmId());
        sb.append(", clientId=");
        sb.append(adminEvent.getAuthDetails().getClientId());
        sb.append(", userId=");
        sb.append(adminEvent.getAuthDetails().getUserId());
        sb.append(", ipAddress=");
        sb.append(adminEvent.getAuthDetails().getIpAddress());
        sb.append(", resourcePath=");
        sb.append(adminEvent.getResourcePath());

        if (adminEvent.getError() != null) {
            sb.append(", error=");
            sb.append(adminEvent.getError());
        }

        return sb.toString();
    }

}