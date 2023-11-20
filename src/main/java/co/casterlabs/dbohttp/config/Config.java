package co.casterlabs.dbohttp.config;

import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.json.annotating.JsonClass;
import lombok.ToString;

@ToString
@JsonClass(exposeAll = true)
public class Config {
    public boolean debug = false;
    public int port = 10243;

    public DatabaseConfig database = new DatabaseConfig();

    public @Nullable String heartbeatUrl = null;
    public long heartbeatIntervalSeconds = TimeUnit.SECONDS.toMillis(15);

}
