package co.casterlabs.dbohttp.database;

import java.io.Closeable;
import java.util.List;

import co.casterlabs.dbohttp.util.MarshallingContext;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.NonNull;

public interface Database extends Closeable {

    public @NonNull QueryResult query(@NonNull MarshallingContext context, @NonNull String query, @NonNull JsonArray parameters) throws UnsupportedOperationException, IllegalArgumentException, QueryException;

    public @NonNull JsonObject generateReport();

    public @NonNull List<String> listTables();

}
