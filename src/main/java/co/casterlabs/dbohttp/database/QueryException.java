package co.casterlabs.dbohttp.database;

public class QueryException extends Exception {
    private static final long serialVersionUID = 3667592496122782378L;

    public QueryException(String reason) {
        super(reason);
    }

    public QueryException(String reason, Throwable cause) {
        super(reason, cause);
    }

}
