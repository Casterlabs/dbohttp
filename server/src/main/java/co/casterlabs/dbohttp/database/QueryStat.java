package co.casterlabs.dbohttp.database;

import java.util.concurrent.TimeUnit;

public record QueryStat(long timestamp_ns, double took_ms, boolean wasSuccessful) {

    public static final long STATS_TIMEFRAME_S = 5;

    public long expiresAt_ns() {
        return this.timestamp_ns + TimeUnit.SECONDS.toNanos(STATS_TIMEFRAME_S);
    }

}
