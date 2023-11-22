package co.casterlabs.dbohttp;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

import co.casterlabs.dbohttp.util.Misc;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Heartbeat extends Thread implements Closeable {
    private boolean shouldRun = true;

    protected Heartbeat() {
        this.setName("Heartbeat Thread");
        this.setPriority(Thread.MIN_PRIORITY);
        this.setDaemon(true);
    }

    @Override
    public void run() {
        while (this.shouldRun) {
            try {
                String response = Misc.httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(DBOHTTP.config.heartbeatUrl))
                        .header("Content-Type", "text/plain")
                        .GET()
//                        .POST(
//                            HttpRequest.BodyPublishers.ofString(
//                                String.valueOf(System.currentTimeMillis())
//                            )
//                        )
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                ).body();
                FastLogger.logStatic(LogLevel.DEBUG, "Sent heartbeat.\n%s", response);
            } catch (IOException | InterruptedException e) {
                FastLogger.logStatic(LogLevel.WARNING, "Unable to send heartbeat:\n%s", e);
            }

            try {
                TimeUnit.SECONDS.sleep(DBOHTTP.config.heartbeatIntervalSeconds);
            } catch (InterruptedException ignored) {}
        }
    }

    @Override
    public void close() throws IOException {
        this.shouldRun = false;
    }

}
