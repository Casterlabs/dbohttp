package co.casterlabs.dbohttp.daemon.config;

import co.casterlabs.rakurai.json.annotating.JsonClass;
import lombok.ToString;

@ToString
@JsonClass(exposeAll = true)
public class Config {
    public AlertConfig alerts = new AlertConfig();
    public DatabaseConfig database = new DatabaseConfig();

}
