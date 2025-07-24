package co.casterlabs.dbohttp.database;

import java.io.Closeable;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import co.casterlabs.dbohttp.util.MarshallingContext;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public abstract class Database implements Closeable {
    protected Deque<QueryStat> stats = new ConcurrentLinkedDeque<>();
    protected volatile long queriesTotal = 0;
    protected volatile boolean isClosed = false;

    protected Database() {
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

    public abstract @NonNull QueryResult query(@NonNull MarshallingContext context, @NonNull String query, @NonNull JsonArray parameters) throws UnsupportedOperationException, IllegalArgumentException, QueryException;

    // Override to add your own fields.
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

    public abstract @NonNull List<String> listTables();

}
