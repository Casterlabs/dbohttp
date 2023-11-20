package co.casterlabs.dbohttp.database;

public class QueryException extends Exception {
    private static final long serialVersionUID = 3667592496122782378L;

    public final String code;

    public QueryException(String code, String reason) {
        super(reason);
        this.code = code;
    }

}
