package co.casterlabs.dbohttp.database;

import java.util.concurrent.TimeUnit;

public record QueryStat(long timestamp_ns, double took_ms) {
    private static final long STATS_TIMEFRAME = TimeUnit.SECONDS.toNanos(5);

    public long expiresAt_ns() {
        return this.timestamp_ns + STATS_TIMEFRAME;
    }

}
