package co.casterlabs.dbohttp.config;

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
    public long heartbeatIntervalSeconds = 15;

    /*
     * Testing token:
     * eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkYm9odHRwIiwiaXNzIjoiRXhhbXBsZSBUb2tlbiIsImlhdCI6MTUxNjIzOTAyMiwiaW5mbyI6dHJ1ZSwicXVlcnkiOnRydWV9.qIqK_J71ThkD8Ki7n4DpuIrc691pHl2SmbJhJXcf520
     */
    public String jwtSecret = "CHANGEMEPLEASE";

}
