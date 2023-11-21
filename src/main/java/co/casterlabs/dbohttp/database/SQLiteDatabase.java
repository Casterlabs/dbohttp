package co.casterlabs.dbohttp.database;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

import co.casterlabs.dbohttp.config.DatabaseConfig;
import co.casterlabs.dbohttp.database.QueryException.QueryErrorCode;
import co.casterlabs.dbohttp.util.MarshallingContext;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.NonNull;
import lombok.SneakyThrows;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class SQLiteDatabase implements Database {
    private final Semaphore concurrentAccessLock = new Semaphore(1);
    private volatile boolean allowFurtherAccess = true;
    private Connection conn;

    private Deque<QueryStat> stats = new LinkedList<>();
    private long queriesTotal = 0;

    public SQLiteDatabase(DatabaseConfig config) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + config.file);
        this.conn.setAutoCommit(false);
    }

    private PreparedStatement prepare(@NonNull MarshallingContext context, @NonNull String query, @NonNull JsonArray parameters) throws UnsupportedOperationException, IllegalArgumentException, QueryException {
        try {
            PreparedStatement prepared = this.conn.prepareStatement(query);

            if (prepared.getParameterMetaData().getParameterCount() != parameters.size()) {
                throw new QueryException(
                    QueryErrorCode.PREPARATION_ERROR,
                    String.format(
                        "An incorrect amount of parameters were specified. Expected %d got %d.",
                        prepared.getParameterMetaData().getParameterCount(), parameters.size()
                    )
                );
            }

            for (int idx = 0; idx < parameters.size(); idx++) {
                Object obj = context.jsonToJava(parameters.get(idx));

                if (obj instanceof byte[]) {
                    prepared.setBytes(idx + 1, (byte[]) obj);
                    continue;
                }

                prepared.setObject(idx + 1, obj);
            }

            return prepared;
        } catch (SQLException e) {
            checkForSpecificError(e);
            FastLogger.logStatic(LogLevel.SEVERE, "Error whilst preparing statement.\n%s", e);
            throw new QueryException(QueryErrorCode.PREPARATION_ERROR, "Error whilst preparing statement.");
        }
    }

    @Override
    public @NonNull QueryResult query(@NonNull MarshallingContext context, @NonNull String query, @NonNull JsonArray parameters) throws UnsupportedOperationException, IllegalArgumentException, QueryException {
        if (!this.allowFurtherAccess) {
            throw new QueryException(QueryErrorCode.INTERNAL_ERROR, "Database is closing.");
        }

        long start = System.nanoTime();

        try {
            this.concurrentAccessLock.acquire();
        } catch (InterruptedException ignored) {
            throw new QueryException(QueryErrorCode.INTERNAL_ERROR, "Internal error.");
        }

        PreparedStatement statement = null;
        boolean dirty = false;

        try {
            statement = this.prepare(context, query, parameters);
            ResultSet resultSet = null;

            try {
                if (statement.execute()) {
                    resultSet = statement.getResultSet();
                }
                dirty = true;
            } catch (SQLException e) {
                checkForSpecificError(e);
                FastLogger.logStatic(LogLevel.SEVERE, "An error occurred whilst executing query.\n%s", e);
                throw new QueryException(QueryErrorCode.FAILED_TO_EXECUTE, "An error occurred whilst executing query.");
            }

            ResultSetMetaData metadata = resultSet == null ? null : resultSet.getMetaData();
            List<JsonObject> rows = Collections.emptyList();

            // We want to skip the row marshalling process if we can...
            if (metadata != null && metadata.getColumnCount() > 0) {
                rows = new LinkedList<>(); // Allocate a list...

                // Get the column names.
                String[] columns = new String[metadata.getColumnCount()];
                for (int i = 0; i < columns.length; i++) {
                    columns[i] = metadata.getColumnLabel(i + 1);
                }

                while (resultSet.next()) {
                    JsonObject row = new JsonObject();
                    for (String columnName : columns) {
                        row.put(
                            columnName,
                            context.javaToJson(resultSet.getObject(columnName))
                        );
                    }
                    rows.add(row);
                }
            }

            long now_ns = System.nanoTime();
            double took_ms = (now_ns - start) / 1000000d;

            this.stats.push(new QueryStat(start, took_ms));
            this.queriesTotal++;

            // Clear any old stats while we're here.
            Iterator<QueryStat> it = this.stats.iterator();
            while (it.hasNext()) {
                QueryStat stat = it.next();
                long expiresAt_ns = stat.expiresAt_ns();

                if (expiresAt_ns < now_ns) {
                    it.remove();
                } else if (expiresAt_ns > now_ns) {
                    break; // Everything after this point will not be expired.
                }
            }

//            FastLogger.logStatic(LogLevel.DEBUG, "Ran `%s` in %fms, rows returned: %d.", query, took, rows.size());

            this.conn.commit();
            return new QueryResult(rows, took_ms);
        } catch (Throwable t) {
            if (dirty) {
                try {
                    this.conn.rollback();
                } catch (SQLException e) {
                    // Not possible... I think?
                    FastLogger.logStatic(LogLevel.SEVERE, "An error occurred whilst rolling back, the database may be busted!\n%s", e);
                }
            }

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
            this.concurrentAccessLock.release();
        }
    }

    @Override
    public JsonObject generateReport() {
        int queued = this.concurrentAccessLock.getQueueLength();

        // Calculate the average query time using the samples. We're not worried about
        // concurrent access or anything. Approximate values are acceptable.
        double averageQueryTime = 0;
        int queriesLogged = 0;

        List<QueryStat> stats = new ArrayList<>(this.stats);
        long now_ns = System.nanoTime();

        for (QueryStat stat : stats) {
            long expiresAt_ns = stat.expiresAt_ns();
            if (now_ns > expiresAt_ns) continue; // Expired, skip it (removing does nothing).

            averageQueryTime += stat.took_ms();
            queriesLogged++;
        }

        if (queriesLogged > 1) {
            averageQueryTime /= queriesLogged; // Don't forget to divide!
        } // Otherwise, leave it as -1.

        return new JsonObject()
            .put("queued", queued)
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
            .map((r) -> r.getString("name"))
            .toList();
    }

    @Override
    public void close() throws IOException {
        if (this.conn == null) return;

        try {
            try {
                this.allowFurtherAccess = false;
                this.concurrentAccessLock.acquire(); // Wait for remaining queries to finish.
                this.conn.close();
            } catch (InterruptedException e) {
                Thread.interrupted(); // Clear.
                this.conn.close(); // We want to close the database regardless.
                Thread.currentThread().interrupt();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        } finally {
            this.conn = null;
        }
    }

    private static void checkForSpecificError(SQLException e) throws QueryException {
        switch (e.getErrorCode()) {
            case 1: {
                String message = e.getMessage();

                if (message.endsWith(")")) {
                    message = message.substring(message.indexOf('(') + 1, message.length() - 1);
                }

                throw new QueryException(QueryErrorCode.SQL_ERROR, message);
            }

            case 5:
            case 6:
            case 11:
                throw new QueryException(QueryErrorCode.INTERNAL_ERROR, "Database is busy, locked, or corrupt. Is the filesystem broken?");

            case 22:
                throw new QueryException(QueryErrorCode.INTERNAL_ERROR, "Filesystem does not support Large Files.");

            case 18:
                throw new QueryException(QueryErrorCode.FAILED_TO_EXECUTE, "Query or parameters (text/blob) are too big for the database to handle.");

            case 20:
                throw new QueryException(QueryErrorCode.FAILED_TO_EXECUTE, "Type mismatch.");
        }
    }

}
