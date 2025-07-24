package co.casterlabs.dbohttp.database.impl;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import co.casterlabs.dbohttp.config.DatabaseConfig;
import co.casterlabs.dbohttp.database.Database;
import co.casterlabs.dbohttp.database.QueryException;
import co.casterlabs.dbohttp.database.QueryException.QueryErrorCode;
import co.casterlabs.dbohttp.database.QueryResult;
import co.casterlabs.dbohttp.database.QueryStat;
import co.casterlabs.dbohttp.util.MarshallingContext;
import co.casterlabs.dbohttp.util.Profiler;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.TypeToken;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.annotating.JsonDeserializationMethod;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import co.casterlabs.rakurai.json.validation.JsonValidationException;
import lombok.NonNull;
import lombok.SneakyThrows;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class RQLiteDatabase implements Database {
    private static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

    private final OkHttpClient client = new OkHttpClient();
    private final String connectionUrl;

    private volatile boolean isClosed = false;

    private Deque<QueryStat> stats = new ConcurrentLinkedDeque<>();
    private long queriesTotal = 0;

    public RQLiteDatabase(DatabaseConfig config) throws SQLException {
        this.connectionUrl = config.connectionString + "?blob_array&associative";

        Thread cleanupThread = new Thread(() -> {
            try {
                while (!this.isClosed) {
                    if (this.stats.size() > 10000) {
                        // Limit it to 10k, I doubt we'll hit 10k requests per second.
                        // This exists to prevent memory leaking from slow threads.
                        this.stats.clear();
                        FastLogger.logStatic(LogLevel.WARNING, "Stats grew to >100k entries. An emergency clear was performed to prevent leaks.");
                        continue;
                    }

                    QueryStat popped = this.stats.peekFirst();

                    if (popped != null) {
                        long now = System.nanoTime();
                        long exp = popped.expiresAt_ns();

                        if (now > exp) {
                            this.stats.removeFirst();
                            continue; // Immediately loop back around. We only sleep when we're done.
                        }
                    }

                    Thread.sleep(1000);
                }
            } catch (Throwable t) {
                t.printStackTrace(); // TODO aaaaaaaaaaaaaaaaaaaa
            }
        });
        cleanupThread.setName("Stats cleanup thread.");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    @Override
    public @NonNull QueryResult query(@NonNull MarshallingContext context, @NonNull String query, @NonNull JsonArray parameters) throws UnsupportedOperationException, IllegalArgumentException, QueryException {
        if (this.isClosed) {
            throw new QueryException(QueryErrorCode.INTERNAL_ERROR, "Database is closing.");
        }

        Profiler profiler = new Profiler();

        boolean wasSuccessful = false;

        try {
            JsonArray statement = profiler.start("Statement Preparation", () -> {
                JsonArray arr = new JsonArray();
                arr.add(query);
                parameters.forEach(arr::add);
                return arr;
            });

            RQLiteResponse response;
            try {
                String bodyStr = profiler.start("Statement Execution", () -> {
                    RequestBody body = RequestBody.create(
                        statement.toString(false),
                        APPLICATION_JSON
                    );

                    Request request = new Request.Builder()
                        .url(this.connectionUrl)
                        .post(body)
                        .build();

                    Call call = this.client.newCall(request);
                    try (Response res = call.execute()) {
                        return res.body().string();
                    }
                });

                response = profiler.start("Result Marshalling", () -> {
                    return Rson.DEFAULT.fromJson(bodyStr, RQLiteResponse.class);
                });
            } catch (Throwable e) {
                FastLogger.logStatic(LogLevel.SEVERE, "An error occurred whilst executing query.\n%s", e);
                throw new QueryException(QueryErrorCode.FAILED_TO_EXECUTE, "An error occurred whilst executing query.");
            }

            // We want to skip the row marshalling process if we can...
            List<Map<String, JsonElement>> rows;
            if (response.results.size() == 0) {
                profiler.log("Result Marshalling", 0);
                rows = Collections.emptyList();
            } else {
                rows = response.results.get(0).rows;
            }

//            FastLogger.logStatic(LogLevel.DEBUG, "Ran `%s` in %fms, rows returned: %d.", query, took, rows.size());

            wasSuccessful = true;

            return new QueryResult(rows, profiler /* mutable */);
        } catch (Throwable t) {
            if (t instanceof QueryException) {
                throw (QueryException) t;
            } else if (t instanceof UnsupportedOperationException) {
                throw (UnsupportedOperationException) t;
            } else if (t instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) t;
            }

            FastLogger.logStatic(LogLevel.SEVERE, "An internal error occurred.\n%s", t);
            throw new QueryException(QueryErrorCode.INTERNAL_ERROR, "Internal error.");
        } finally {
            this.stats.add(new QueryStat(System.nanoTime(), profiler.timeSpent_ms, wasSuccessful));
            this.queriesTotal++;
        }
    }

    @Override
    public JsonObject generateReport() {
        // Calculate the average query time using the samples. We're not worried about
        // concurrent access or anything. Approximate values are acceptable.
        double averageQueryTime = 0;
        int queriesLogged = 0;

        int successes = 0;

        long now_ns = System.nanoTime();
        for (QueryStat stat : this.stats) {
            if (now_ns > stat.expiresAt_ns()) continue; // Expired, skip it (removing does nothing).

            averageQueryTime += stat.took_ms();
            queriesLogged++;

            if (stat.wasSuccessful()) {
                successes++;
            }
        }

        double successRate = -1;

        if (queriesLogged > 1) {
            averageQueryTime /= queriesLogged; // Don't forget to divide!
            successRate = successes / (double) queriesLogged;
        } // Otherwise, leave it as -1.

        return new JsonObject()
            .put("successRate", successRate)
            .put("queriesRan", this.queriesTotal)
            .put("queriesPerSecond", queriesLogged / (double) QueryStat.STATS_TIMEFRAME_S)
            .put("averageQueryTime", averageQueryTime);
    }

    @SneakyThrows
    @Override
    public @NonNull List<String> listTables() {
        return this.query(
            new MarshallingContext(),
            "SELECT name FROM sqlite_schema WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' ORDER BY 1;",
            JsonArray.EMPTY_ARRAY
        )
            .rows()
            .parallelStream()
            .map((m) -> m.get("name").getAsString())
            .toList();
    }

    @Override
    public void close() throws IOException {
        if (this.isClosed) return;
        this.isClosed = true;
    }

    @JsonClass(exposeAll = true)
    private static final class RQLiteResponse {
        private List<RQLiteResult> results;
    }

    private static final class RQLiteResult {
        private static final TypeToken<Map<String, JsonElement>> ROW_TT = new TypeToken<>() {
        };

        private List<Map<String, JsonElement>> rows = new LinkedList<>();

        @JsonDeserializationMethod("rows")
        private void $deserialize_rows(JsonElement e) throws JsonValidationException, JsonParseException {
            for (JsonElement row : e.getAsArray()) {
                this.rows.add(Rson.DEFAULT.fromJson(row, ROW_TT));
            }
        }

    }

}
