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

public class SQLiteDatabase implements Database {
    private Connection conn;
    private Semaphore concurrentAccessLock;

    private DatabaseConfig config;

    public SQLiteDatabase(DatabaseConfig config) throws SQLException {
        this.config = config;

        this.conn = DriverManager.getConnection("jdbc:sqlite:" + this.config.file);
        this.conn.setAutoCommit(true);

        this.concurrentAccessLock = new Semaphore(this.config.concurrentAccessLimit);
    }

    private PreparedStatement prepare(@NonNull MarshallingContext context, @NonNull String query, @Nullable JsonArray parameters) throws StatementPreparationException, QueryMarshallingException {
        try {
            PreparedStatement prepared = this.conn.prepareStatement(query);
            if (parameters != null) {
                try {
                    for (int idx = 0; idx < parameters.size(); idx++) {
                        Object obj = context.jsonToJava(parameters.get(idx));

                        if (obj instanceof byte[]) {
                            prepared.setBytes(idx + 1, (byte[]) obj);
                            continue;
                        }

                        prepared.setObject(idx + 1, obj);
                    }
                } catch (Throwable t) {
                    throw new QueryMarshallingException(t);
                }
            }

            return prepared;
        } catch (SQLException e) {
            throw new StatementPreparationException(e);
        }
    }

    @Override
    public @NonNull List<JsonObject> query(@NonNull MarshallingContext context, @NonNull String query, @Nullable JsonArray parameters) throws StatementPreparationException, QueryException, QueryMarshallingException, InterruptedException {
        this.concurrentAccessLock.acquire();

        try (PreparedStatement statement = this.prepare(context, query, parameters)) {
            ResultSet resultSet;

            try {
                resultSet = statement.executeQuery();
            } catch (SQLException e) {
                throw new QueryException("An error occurred whilst executing query.", e);
            }

            try {
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

                return results;
            } catch (Throwable t) {
                throw new QueryMarshallingException(t);
            }
        } catch (SQLException e) {
            throw new QueryException("An internal error occurred whilst cleaning up.", e);
        } finally {
            this.concurrentAccessLock.release();
        }
    }

    @Override
    public void close() throws IOException {
        if (this.conn == null) return;

        try {
            try {
                this.concurrentAccessLock.acquire(this.config.concurrentAccessLimit);
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
