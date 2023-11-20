package co.casterlabs.dbohttp;

import java.util.concurrent.TimeUnit;

import co.casterlabs.commons.async.AsyncTask;
import co.casterlabs.dbohttp.config.Config;
import co.casterlabs.dbohttp.database.Database;
import lombok.SneakyThrows;

public class DBOHTTP {
    public static Config config;
    public static Daemon daemon;
    public static Database database;

    static {
        AsyncTask.create(DBOHTTP::doHeartbeatLoop);
    }

    @SneakyThrows
    private static void doHeartbeatLoop() {
        while (true) {
            TimeUnit.SECONDS.sleep(15);
            if (config == null) continue;

        }
    }

}
