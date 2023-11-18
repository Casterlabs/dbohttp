package co.casterlabs.dbohttp.daemon;

import co.casterlabs.dbohttp.daemon.config.DatabaseConfig;
import co.casterlabs.dbohttp.daemon.database.Database;
import co.casterlabs.dbohttp.daemon.database.SQLiteDatabase;
import co.casterlabs.dbohttp.daemon.util.MarshallingContext;
import co.casterlabs.rakurai.json.element.JsonArray;

public class Test {

    public static void main(String[] args) throws Exception {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.file = ":memory:";

        Database db = new SQLiteDatabase(dbConfig);

        MarshallingContext context = new MarshallingContext();
        context.byteArraysAreSigned = true;

        db.run(context, "CREATE TABLE test(some_n INTEGER, some_s TEXT, some_b BLOB);", null);
        for (int i = 0; i < 100; i++) {
            JsonArray parameters = JsonArray.of(i, String.valueOf(Math.random()), new byte[1024]);
            db.run(context, "INSERT INTO test (some_n, some_s, some_b) VALUES(?, ?, ?);", parameters);
        }

        long start = System.currentTimeMillis();
        var result = db.all(context, "SELECT * FROM test;", null);
        long end = System.currentTimeMillis();

        System.out.printf("%dms\n", end - start);
        System.out.printf("%s items\n", result.size());
    }

}
