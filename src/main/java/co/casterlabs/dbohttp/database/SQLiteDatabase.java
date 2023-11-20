package co.casterlabs.dbohttp.database;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.dbohttp.config.DatabaseConfig;
import co.casterlabs.dbohttp.util.MarshallingContext;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class SQLiteDatabase implements Database {
    private final Semaphore concurrentAccessLock = new Semaphore(1);
    private Connection conn;

    public SQLiteDatabase(DatabaseConfig config) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + config.file);
        this.conn.setAutoCommit(false);
    }

    private PreparedStatement prepare(@NonNull MarshallingContext context, @NonNull String query, @Nullable JsonArray parameters) throws UnsupportedOperationException, IllegalArgumentException, QueryException {
        try {
            PreparedStatement prepared = this.conn.prepareStatement(query);
            if (parameters != null) {
                for (int idx = 0; idx < parameters.size(); idx++) {
                    Object obj = context.jsonToJava(parameters.get(idx));

                    if (obj instanceof byte[]) {
                        prepared.setBytes(idx + 1, (byte[]) obj);
                        continue;
                    }

                    prepared.setObject(idx + 1, obj);
                }
            }

            return prepared;
        } catch (SQLException e) {
            FastLogger.logStatic(LogLevel.SEVERE, "Error whilst preparing statement.\n%s", e);
            throw new QueryException("PREPARATION_ERROR", "Error whilst preparing statement.");
        }
    }

    @Override
    public @NonNull List<JsonObject> query(@NonNull MarshallingContext context, @NonNull String query, @Nullable JsonArray parameters) throws UnsupportedOperationException, IllegalArgumentException, QueryException {
        try {
            this.concurrentAccessLock.acquire();
        } catch (InterruptedException ignored) {
            throw new QueryException("INTERNAL_ERROR", "Internal error.");
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
                FastLogger.logStatic(LogLevel.SEVERE, "An error occurred whilst executing query.\n%s", e);
                throw new QueryException("FAILED_TO_EXECUTE", "An error occurred whilst executing query.");
            }

            if (resultSet == null) {
                this.conn.commit();
                return Collections.emptyList();
            }

            // Get the column names.
            ResultSetMetaData metadata = resultSet.getMetaData();
            if (metadata.getColumnCount() == 0) {
                return Collections.emptyList();
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

            this.conn.commit();

            return results;
        } catch (Throwable t) {
            if (statement != null) {
                try {
                    this.conn.rollback();
                } catch (SQLException e) {
                    // Not possible... I think?
                    FastLogger.logStatic(LogLevel.SEVERE, "An error occurred whilst rolling back, the database may be busted!\n%s", e);
                }
            }

            FastLogger.logStatic(LogLevel.SEVERE, "An internal error occurred.\n%s", t);
            throw new QueryException("INTERNAL_ERROR", "Internal error.");
        } finally {
            this.concurrentAccessLock.release();
        }
    }

    @Override
    public void close() throws IOException {
        if (this.conn == null) return;

        try {
            try {
                this.concurrentAccessLock.acquire(); // Wait for remaining queries to finish.
                this.conn.close();
            } catch (InterruptedException e) {
                Thread.interrupted(); // Clear.
                this.conn.close(); // We want to close the database regardless.
                Thread.currentThread().interrupt();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

}
