package co.casterlabs.dbohttp.database;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
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

    private long queriesRan = 0;
    private double[] queryTimeSamples = new double[100];
    {
        Arrays.fill(this.queryTimeSamples, -1);
    }

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

        try {
            statement = this.prepare(context, query, parameters);
            ResultSet resultSet = null;

            try {
                if (statement.execute()) {
                    resultSet = statement.getResultSet();
                }
            } catch (SQLException e) {
                checkForSpecificError(e);
                FastLogger.logStatic(LogLevel.SEVERE, "An error occurred whilst executing query.\n%s", e);
                throw new QueryException(QueryErrorCode.FAILED_TO_EXECUTE, "An error occurred whilst executing query.");
            }

            if (resultSet == null) {
                double took = (System.nanoTime() - start) / 1e-6;

                // We want to write to the samples array, over writing previous values as we go.
                // This is effectively a circular array.
                this.queryTimeSamples[(int) (this.queriesRan % this.queryTimeSamples.length)] = took;
                this.queriesRan++;

                FastLogger.logStatic(LogLevel.DEBUG, "Ran `%s` in %fms.", query, took);

                this.conn.commit();
                return new QueryResult(Collections.emptyList(), took);
            }

            // Get the column names.
            ResultSetMetaData metadata = resultSet.getMetaData();
            if (metadata.getColumnCount() == 0) {
                double took = (System.nanoTime() - start) / 1e-6;

                // We want to write to the samples array, over writing previous values as we go.
                // This is effectively a circular array.
                this.queryTimeSamples[(int) (this.queriesRan % this.queryTimeSamples.length)] = took;
                this.queriesRan++;

                FastLogger.logStatic(LogLevel.DEBUG, "Ran `%s` in %fms.", query, took);

                this.conn.commit();
                return new QueryResult(Collections.emptyList(), took);
            }

            String[] columns = new String[metadata.getColumnCount()];
            for (int i = 0; i < columns.length; i++) {
                columns[i] = metadata.getColumnLabel(i + 1);
            }

            List<JsonObject> results = new LinkedList<>();
            while (resultSet.next()) {
                JsonObject row = new JsonObject();
                for (String columnName : columns) {
                    row.put(
                        columnName,
                        context.javaToJson(resultSet.getObject(columnName))
                    );
                }
                results.add(row);
            }

            double took = (System.nanoTime() - start) / 1e-6;

            // We want to write to the samples array, over writing previous values as we go.
            // This is effectively a circular array.
            this.queryTimeSamples[(int) (this.queriesRan % this.queryTimeSamples.length)] = took;
            this.queriesRan++;

            FastLogger.logStatic(LogLevel.DEBUG, "Ran `%s` in %fms.", query, took);

            this.conn.commit();
            return new QueryResult(results, took);
        } catch (Throwable t) {
            if (statement != null) {
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
        // Calculate the average query time using the samples. We're not worried about
        // concurrent access or anything. Approximate values are acceptable.
        double averageQueryTime = -1;
        int averageQueryTime_sampleCount = 0;

        for (double sample : this.queryTimeSamples) {
            if (sample == -1) continue;
            averageQueryTime += sample;
            averageQueryTime_sampleCount++;
        }

        if (averageQueryTime_sampleCount > 0) {
            averageQueryTime /= averageQueryTime_sampleCount; // Don't forget to divide!
        } // Otherwise, leave it as -1.

        return new JsonObject()
            .put("queued", this.concurrentAccessLock.getQueueLength())
            .put("queriesRan", this.queriesRan)
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
