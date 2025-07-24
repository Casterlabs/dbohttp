package co.casterlabs.dbohttp.config;

import java.io.IOException;
import java.sql.SQLException;

import co.casterlabs.dbohttp.database.Database;
import co.casterlabs.dbohttp.database.impl.RQLiteDatabase;
import co.casterlabs.dbohttp.database.impl.SQLiteDatabase;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.annotating.JsonDeserializationMethod;
import co.casterlabs.rakurai.json.element.JsonElement;
import lombok.ToString;

@ToString
@JsonClass(exposeAll = true)
public class DatabaseConfig {
    public long accessTimeoutSeconds = 30;

    public DatabaseDriver driver = DatabaseDriver.SQLITE;
    public String connectionString = "database.sqlite";

    public Database create() throws IOException {
        try {
            switch (this.driver) {
                case SQLITE:
                    return new SQLiteDatabase(this);
                case RQLITE:
                    return new RQLiteDatabase(this);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return null; // Compiler...
    }

    // Legacy
    @JsonDeserializationMethod("file")
    private void $deserialize_file(JsonElement e) {
        this.connectionString = e.getAsString();
    }

    public static enum DatabaseDriver {
        SQLITE,
        RQLITE,
    }

}
