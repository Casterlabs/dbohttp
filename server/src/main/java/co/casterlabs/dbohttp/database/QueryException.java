package co.casterlabs.dbohttp.database;

public class QueryException extends Exception {
    private static final long serialVersionUID = 3667592496122782378L;

    public final QueryErrorCode code;

    public QueryException(QueryErrorCode code, String reason) {
        super(reason);
        this.code = code;
    }

    public enum QueryErrorCode {
        INTERNAL_ERROR,
        PREPARATION_ERROR,
        FAILED_TO_EXECUTE,
        SQL_ERROR

    }

}
