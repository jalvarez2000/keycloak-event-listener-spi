package com.juganu.juganueventlistenerprovider.provider;

import com.juganu.juganutoken.JuganuCredentialsActionToken;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import org.keycloak.email.DefaultEmailSenderProvider;
import org.keycloak.email.EmailException;
import org.keycloak.authentication.actiontoken.resetcred.ResetCredentialsActionToken;
import org.keycloak.sessions.AuthenticationSessionCompoundId;

import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.Charset;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.mail.internet.MimeBodyPart;


// ------------ COMENTARIOS PARA OMAR --------------------
// Para compilar: mvn install. Esto te genera un fichero juganu-event-listener.jar
// Para deploy: Copias en caliente este archivo en el directorio standalone/deployments/ de donde esté corriendo keycloak (por ejemplo, /opt/jboss/keycloak/)
// Docker: Para poder desplegarlo para las pruebas de la semana que viene me he creado en mi entorno y he subido un docker con este JAR. Puedes ver en Dockerfile/deployment el DOckerfile con la forma de hacerlo.


// Notas sobre el desarrollo: Básicamente estoy intentadno generar un "Juganu Token" con una periocidad limitada que sirva para cambiar la contraseña de un usuario en específico (función obtainResetTokenId), pero no lo consigo.
// Hay muchas otras posiblidades siguiendo el modelo de Keycloak que estaba estudiando pero ya no me ha dado tiempo (mediante "insertanddo" nuestro flujo de autenticación por ejemplo, echa un vistazo por ejemplo a DefaultAuthenticationFlows.java en el código de Keycloak y ResetCredentialsEmail.java.


public class JuganuEventListenerProvider implements EventListenerProvider {

    private final KeycloakSession session;

    public JuganuEventListenerProvider(KeycloakSession session) {
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
            String token = obtainResetTokenId(session,userId,user.getEmail(),adminEvent.getAuthDetails().getClientId());                try {
                String template = readTemplate("activate-your-account.html");
                template = replacePlaceholder(template,"$EMAIL",user.getEmail());
                sendMail(user, "CREATE USER",template);
            }
            catch (Exception e) {
                System.out.println("Error reading template from resource file: " + e);
            };
        }
    }

    @Override
    public void close() {

    }

    private String obtainResetTokenId(KeycloakSession session, String userId, String emailId, String clientId) {
        String id = "";
        /*session.getContext()
                .getRealm()
                .getClientAuthenticationFlow()
                .getAuthenticationSession();*/


        /*session.authenticationSessions().createRootAuthenticationSession(session.realms().getRealm(event.getRealmId()))
                .createAuthenticationSession(clientId)
        String authSessionEncodedId = AuthenticationSessionCompoundId.fromAuthSession(session.getContext().getAuthenticationSession()).getEncodedId();*/
        JuganuCredentialsActionToken token = new JuganuCredentialsActionToken(userId,2592000,clientId);
        KeycloakContext context = this.session.getContext();

        System.out.println("Token" + token.serialize(session, context.getRealm(),context.getUri()));

        return id;
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