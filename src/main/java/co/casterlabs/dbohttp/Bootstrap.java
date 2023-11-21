package co.casterlabs.dbohttp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import co.casterlabs.dbohttp.config.Config;
import co.casterlabs.dbohttp.daemon.Daemon;
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
        System.setProperty("fastloggingframework.wrapsystem", "true");
        FastLoggingFramework.setColorEnabled(false);

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

//        boolean isNew = DBOHTTP.config == null;
        DBOHTTP.config = config;

        // Reconfigure the JWT verifiers.
        Algorithm signingAlg = Algorithm.HMAC256(config.jwtSecret);

        DBOHTTP.infoVerifier = JWT.require(signingAlg)
            .withClaim("info", true)
            .withSubject("dbohttp")
            .build();
        DBOHTTP.queryVerifier = JWT.require(signingAlg)
            .withClaim("query", true)
            .withSubject("dbohttp")
            .build();

        // Reconfigure heartbeats.
        if (DBOHTTP.heartbeat != null) {
            DBOHTTP.heartbeat.close();
            DBOHTTP.heartbeat = null;
        }

        if (config.heartbeatUrl != null && config.heartbeatIntervalSeconds > 0) {
            DBOHTTP.heartbeat = new Heartbeat();
            DBOHTTP.heartbeat.start();
        }

        // Start the daemon if necessary.
        if (DBOHTTP.daemon == null) {
            DBOHTTP.daemon = new Daemon(config.port);
            DBOHTTP.daemon.open();
        } else {
            if (DBOHTTP.config.port != config.port) {
                FastLogger.logStatic(LogLevel.WARNING, "DBOHTTP does not support changing the HTTP server port while running. You will need to fully restart for this to take effect.");
            }
        }

        // Logging
        FastLoggingFramework.setDefaultLevel(config.debug ? LogLevel.DEBUG : LogLevel.INFO);
        DBOHTTP.daemon.server.getLogger().setCurrentLevel(FastLoggingFramework.getDefaultLevel());

        // Reconfigure the database.
        Database oldDb = DBOHTTP.database;
        DBOHTTP.database = config.database.create();
        if (oldDb != null) {
            oldDb.close();
        }
    }

}
