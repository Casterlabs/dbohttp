package co.casterlabs.dbohttp;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
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
        this.start();
    }

    @Override
    public void run() {
        while (this.shouldRun) {
            try {
                Misc.httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(DBOHTTP.config.heartbeatUrl))
                        .header("Content-Type", "text/plain")
                        .POST(
                            HttpRequest.BodyPublishers.ofString(
                                String.valueOf(System.currentTimeMillis())
                            )
                        )
                        .build(),
                    null
                );
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
