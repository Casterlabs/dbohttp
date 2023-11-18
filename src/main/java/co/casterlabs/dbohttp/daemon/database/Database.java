package co.casterlabs.dbohttp.daemon.database;

import java.io.Closeable;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.dbohttp.daemon.util.MarshallingContext;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.NonNull;

public interface Database extends Closeable {

    public @NonNull JsonElement first(@NonNull MarshallingContext context, @Nullable String columnName, @NonNull String query, @Nullable JsonArray parameters) throws StatementPreparationException, QueryException, QueryMarshallingException;

    public @NonNull List<JsonObject> all(@NonNull MarshallingContext context, @NonNull String query, @Nullable JsonArray parameters) throws StatementPreparationException, QueryException, QueryMarshallingException;

    public void run(@NonNull MarshallingContext context, @NonNull String query, @Nullable JsonArray parameters) throws StatementPreparationException, QueryException, QueryMarshallingException;

}
