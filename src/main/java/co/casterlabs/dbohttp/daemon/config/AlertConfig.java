package co.casterlabs.dbohttp.daemon.config;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import co.casterlabs.dbohttp.daemon.config.AlertConfig.Alert;
import co.casterlabs.dbohttp.daemon.util.Misc;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.annotating.JsonExclude;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.ToString;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

@ToString
@JsonClass(exposeAll = true)
public class AlertConfig {
    public @JsonExclude Alert[] errorAlerts = {};

    @ToString
    @JsonClass(exposeAll = true)
    public static class Alert {
        public AlertType type = AlertType.HTTP_PLAINTEXT;
        public Map<String, String> properties;

        private @JsonExclude Consumer<String> handler;

        public void doSend(@NonNull String message) {
            if (this.handler == null) {
                this.handler = this.type.create.apply(this);
            }
            try {
                this.handler.accept(message);
                FastLogger.logStatic(LogLevel.DEBUG, "Successfully sent webhook: %s\n%s", this, message);
            } catch (Throwable t) {
                FastLogger.logStatic(LogLevel.WARNING, "Unable to send webhook: %s\n%s", this, t);
            }
        }
    }

    @AllArgsConstructor
    public enum AlertType {
        HTTP_PLAINTEXT(HttpPlainTextImpl::new),
        DISCORD_COMPATIBLE(DiscordCompatibleImpl::new),
        SLACK_COMPATIBLE(SlackCompatibleImpl::new),
        ;

        private Function<Alert, Consumer<String>> create;
    }
}

class HttpPlainTextImpl implements Consumer<String> {
    private Alert alert;

    public HttpPlainTextImpl(Alert alert) {
        this.alert = alert;
    }

    @SneakyThrows
    @Override
    public void accept(String message) {
        Misc.httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(this.alert.properties.get("http.url")))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(message))
                .build(),
            null
        );
    }
}

class DiscordCompatibleImpl implements Consumer<String> {
    private Alert alert;

    public DiscordCompatibleImpl(Alert alert) {
        this.alert = alert;
    }

    @SneakyThrows
    @Override
    public void accept(String message) {
        Misc.httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(this.alert.properties.get("http.url")))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        JsonObject
                            .singleton("content", message)
                            .toString()
                    )
                )
                .build(),
            null
        );
    }
}

class SlackCompatibleImpl implements Consumer<String> {
    private Alert alert;

    public SlackCompatibleImpl(Alert alert) {
        this.alert = alert;
    }

    @SneakyThrows
    @Override
    public void accept(String message) {
        Misc.httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(this.alert.properties.get("http.url")))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        JsonObject
                            .singleton("text", message)
                            .toString()
                    )
                )
                .build(),
            null
        );
    }
}

//class EmailImpl implements Consumer<String> {
//    private Session session;
//    private Alert alert;
//
//    public EmailImpl(Alert alert) {
//        this.alert = alert;
//
//        Properties prop = new Properties();
//        prop.putAll(this.alert.properties);
//
//        this.session = Session.getInstance(prop, new Authenticator() {
//            @Override
//            protected PasswordAuthentication getPasswordAuthentication() {
//                return new PasswordAuthentication(
//                    alert.properties.get("mail.smtp.username"),
//                    alert.properties.get("mail.smtp.password")
//                );
//            }
//        });
//
//    }
//
//    @SneakyThrows
//    @Override
//    public void accept(String message) {
//        Message mimeMessage = new MimeMessage(this.session);
//        mimeMessage.setFrom(new InternetAddress(this.alert.properties.get("mail.from")));
//        mimeMessage.setRecipients(
//            Message.RecipientType.TO, InternetAddress.parse(this.alert.properties.get("mail.to"))
//        );
//        mimeMessage.setSubject("DBOHTTP - Alert");
//
//        MimeBodyPart mimeBodyPart = new MimeBodyPart();
//        mimeBodyPart.setContent(message, "text/html; charset=utf-8");
//
//        Multipart multipart = new MimeMultipart();
//        multipart.addBodyPart(mimeBodyPart);
//
//        mimeMessage.setContent(multipart);
//
//        Transport.send(mimeMessage);
//    }
//}
