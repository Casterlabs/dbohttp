package co.casterlabs.dbohttp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import co.casterlabs.dbohttp.config.Config;
import co.casterlabs.dbohttp.database.Database;
import co.casterlabs.dbohttp.util.FileWatcher;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import xyz.e3ndr.fastloggingframework.FastLoggingFramework;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Bootstrap {
    private static final File CONFIG_FILE = new File("config.json");

    public static void main(String[] args) throws IOException {
        new FileWatcher(CONFIG_FILE) {
            @Override
            public void onChange() {
                try {
                    reload();
                    FastLogger.logStatic("Reloaded config!");
                } catch (Throwable t) {
                    FastLogger.logStatic(LogLevel.SEVERE, "Unable to reload config file:\n%s", t);
                }
            }
        }
            .start();

        reload();
    }

    private static void reload() throws IOException {
        if (!CONFIG_FILE.exists()) {
            FastLogger.logStatic("Config file doesn't exist, creating a new file. Modify it and restart DBOHTTP.");
            Files.writeString(
                CONFIG_FILE.toPath(),
                Rson.DEFAULT
                    .toJson(new Config())
                    .toString(true)
            );
            System.exit(1);
        }

        Config config;

        try {
            config = Rson.DEFAULT.fromJson(Files.readString(CONFIG_FILE.toPath()), Config.class);
        } catch (JsonParseException e) {
            FastLogger.logStatic(LogLevel.SEVERE, "Unable to parse config file, is it malformed?\n%s", e);
            return;
        }

        if (DBOHTTP.config == null) {
            DBOHTTP.daemon = new Daemon(config.port);
            DBOHTTP.daemon.open();
        } else {
            if (DBOHTTP.config.port != config.port) {
                FastLogger.logStatic(LogLevel.WARNING, "DBOHTTP does not support changing the HTTP server port while running. You will need to fully restart for this to take effect.");
            }
        }

        DBOHTTP.config = config;

        // Reconfigure heartbeats.
        if (DBOHTTP.heartbeat != null) {
            DBOHTTP.heartbeat.close();
            DBOHTTP.heartbeat = null;
        }

        if (DBOHTTP.config.heartbeatUrl != null && DBOHTTP.config.heartbeatIntervalSeconds > 0) {
            DBOHTTP.heartbeat = new Heartbeat();
            DBOHTTP.heartbeat.start();
        }

        // Logging
        FastLoggingFramework.setColorEnabled(false);
        FastLoggingFramework.setDefaultLevel(config.debug ? LogLevel.DEBUG : LogLevel.INFO);

        // Reconfigure the database.
        Database oldDb = DBOHTTP.database;
        DBOHTTP.database = config.database.create();
        if (oldDb != null) {
            oldDb.close();
        }
    }

}
