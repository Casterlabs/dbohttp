package co.casterlabs.dbohttp.database;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;

import co.casterlabs.dbohttp.config.DatabaseConfig;
import co.casterlabs.dbohttp.database.QueryException.QueryErrorCode;
import co.casterlabs.dbohttp.util.MarshallingContext;
import co.casterlabs.dbohttp.util.Profiler;
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

    private Deque<QueryStat> stats = new ConcurrentLinkedDeque<>();
    private long queriesTotal = 0;

    public SQLiteDatabase(DatabaseConfig config) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + config.file);
        this.conn.setAutoCommit(false);

        Thread cleanupThread = new Thread(() -> {
            try {
                while (this.conn != null && !this.conn.isClosed()) {
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

        Profiler profiler = new Profiler();

        profiler.start("Access Lock Acquisition", () -> {
            try {
                this.concurrentAccessLock.acquire();
            } catch (InterruptedException ignored) {
                throw new QueryException(QueryErrorCode.INTERNAL_ERROR, "Internal error.");
            }
        });

        PreparedStatement statement = null;
        boolean dirty = false;
        boolean wasSuccessful = false;

        try {
            statement = profiler.start("Statement Preparation", () -> this.prepare(context, query, parameters));
            ResultSet resultSet = null;

            try {
                PreparedStatement $statement_ptr = statement;
                boolean hasResult = profiler.start("Statement Execution", () -> $statement_ptr.execute());
                dirty = true;

                if (hasResult) {
                    resultSet = profiler.start("Result Gathering", () -> $statement_ptr.getResultSet());
                }
            } catch (Throwable e) {
                checkForSpecificError(e);
                FastLogger.logStatic(LogLevel.SEVERE, "An error occurred whilst executing query.\n%s", e);
                throw new QueryException(QueryErrorCode.FAILED_TO_EXECUTE, "An error occurred whilst executing query.");
            }

            ResultSetMetaData metadata = resultSet == null ? null : resultSet.getMetaData();
            JsonArray rows = new JsonArray();

            // We want to skip the row marshalling process if we can...
            if (metadata == null || metadata.getColumnCount() == 0) {
                profiler.log("Result Marshalling", 0);
            } else {
                ResultSet $resultSet_ptr = resultSet;
                profiler.start("Result Marshalling", () -> {
                    // Get the column names.
                    String[] columns = new String[metadata.getColumnCount()];
                    for (int i = 0; i < columns.length; i++) {
                        columns[i] = metadata.getColumnLabel(i + 1);
                    }

                    while ($resultSet_ptr.next()) {
                        JsonObject row = new JsonObject();
                        for (String columnName : columns) {
                            row.put(
                                columnName,
                                context.javaToJson($resultSet_ptr.getObject(columnName))
                            );
                        }
                        rows.add(row);
                    }
                });
            }

//            FastLogger.logStatic(LogLevel.DEBUG, "Ran `%s` in %fms, rows returned: %d.", query, took, rows.size());

            profiler.start("Database Commit", () -> this.conn.commit());
            wasSuccessful = true;

            return new QueryResult(rows, profiler /* mutable */);
        } catch (Throwable t) {
            if (dirty) {
                try {
                    profiler.start("Database Rollback", () -> this.conn.rollback());
                } catch (Throwable e) {
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

            PreparedStatement $statement_ptr = statement;
            profiler.start("Statement Cleanup", () -> {
                if ($statement_ptr != null) {
                    try {
                        $statement_ptr.close();
                    } catch (SQLException e) {
                        // Not possible... I think?
                        FastLogger.logStatic(LogLevel.SEVERE, "An error occurred whilst freeing statement, the database may be busted!\n%s", e);
                    }
                }
            });

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
        int queued = this.concurrentAccessLock.getQueueLength();

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

        if (queriesLogged > 1) {
            averageQueryTime /= queriesLogged; // Don't forget to divide!
        } // Otherwise, leave it as -1.

        double successRate = successes / (double) queriesLogged;

        return new JsonObject()
            .put("queued", queued)
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
            .toList()
            .parallelStream()
            .map((r) -> r.getAsObject().getString("name"))
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

    private static void checkForSpecificError(Throwable t) throws QueryException {
        if (!(t instanceof SQLException)) return;

        SQLException e = (SQLException) t;
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
