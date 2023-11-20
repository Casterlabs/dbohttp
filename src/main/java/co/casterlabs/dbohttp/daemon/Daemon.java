package co.casterlabs.dbohttp.daemon;

import java.io.Closeable;
import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.dbohttp.DBOHTTP;
import co.casterlabs.dbohttp.database.QueryException;
import co.casterlabs.dbohttp.database.QueryResult;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rhs.protocol.HttpStatus;
import co.casterlabs.rhs.protocol.StandardHttpStatus;
import co.casterlabs.rhs.server.HttpListener;
import co.casterlabs.rhs.server.HttpResponse;
import co.casterlabs.rhs.server.HttpServer;
import co.casterlabs.rhs.server.HttpServerBuilder;
import co.casterlabs.rhs.session.HttpSession;
import co.casterlabs.rhs.session.WebsocketListener;
import co.casterlabs.rhs.session.WebsocketSession;
import co.casterlabs.rhs.util.DropConnectionException;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Daemon implements Closeable, HttpListener {
    private static final HttpResponse NOT_IMPLEMENTED = HttpResponse.newFixedLengthResponse(StandardHttpStatus.NOT_IMPLEMENTED);

    public final HttpServer server;

    public Daemon(int port) {
        this.server = new HttpServerBuilder()
            .setBehindProxy(true)
            .setPort(port)
            .build(this);
    }

    private HttpResponse handleQuery(HttpSession session) {
        QueryRequestBody request;

        try {
            request = Rson.DEFAULT.fromJson(session.getRequestBody(), QueryRequestBody.class);
        } catch (Throwable t) {
            FastLogger.logStatic(LogLevel.SEVERE, "An error occurred whilst parsing body.\n%s", t);
            return errorResponse(
                StandardHttpStatus.NOT_MODIFIED,
                "BAD_REQUEST",
                "Could not parse your request."
            )
                .putHeader("X-Modified", "no");
        }

        try {
            QueryResult result = DBOHTTP.database.query(request, request.sql, request.params);

            return HttpResponse.newFixedLengthResponse(
                StandardHttpStatus.OK,
                new JsonObject()
                    .put("results", Rson.DEFAULT.toJson(result.rows()))
                    .put(
                        "meta",
                        new JsonObject()
                            .put("took", result.took())
                            .put("rowsReturned", result.rows().size())
                    )
                    .putNull("error")
                    .toString(true)
            )
                .setMimeType("application/json; charset=utf-8")
                .putHeader("X-Modified", "yes");
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            return errorResponse(
                StandardHttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                e.getMessage()
            )
                .putHeader("X-Modified", "no");
        } catch (QueryException e) {
            return errorResponse(
                StandardHttpStatus.INTERNAL_ERROR,
                e.code.name(),
                e.getMessage()
            )
                .putHeader("X-Modified", "no");
        } catch (Throwable t) {
            return errorResponse(
                StandardHttpStatus.INTERNAL_ERROR,
                "INTERNAL_ERROR",
                "An internal error occurred. Database may have been modified."
            )
                .putHeader("X-Modified", "maybe");
        }
    }

    private HttpResponse handleInfo(HttpSession session) {
        try {
            return HttpResponse.newFixedLengthResponse(
                StandardHttpStatus.OK,
                new JsonObject()
                    .put(
                        "info",
                        new JsonObject()
                            .put("driver", DBOHTTP.config.database.driver.name())
                            .put("tables", Rson.DEFAULT.toJson(DBOHTTP.database.listTables()))
                            .put("report", DBOHTTP.database.generateReport())
                    )
                    .putNull("error")
                    .toString(true)
            )
                .setMimeType("application/json; charset=utf-8")
                .putHeader("X-Modified", "no");
        } catch (Throwable t) {
            return errorResponse(
                StandardHttpStatus.INTERNAL_ERROR,
                "INTERNAL_ERROR",
                "An internal error occurred."
            )
                .putHeader("X-Modified", "no");
        }
    }

    @Override
    public @Nullable HttpResponse serveHttpSession(HttpSession session) {
        // TODO Verify Authorization header.
        switch (session.getMethod()) {
            case GET:
                return this.handleInfo(session);
            case POST:
                return this.handleQuery(session);
            default:
                return NOT_IMPLEMENTED;
        }
    }

    @Override
    public @Nullable WebsocketListener serveWebsocketSession(WebsocketSession session) {
        throw new DropConnectionException();
    }

    public void open() throws IOException {
        this.server.start();
    }

    @Override
    public void close() throws IOException {
        this.server.stop();
    }

    private static HttpResponse errorResponse(HttpStatus status, String code, String message) {
        return HttpResponse.newFixedLengthResponse(
            status,
            new JsonObject()
                .putNull("results")
                .putNull("meta")
                .put(
                    "error",
                    new JsonObject()
                        .put("code", code)
                        .put("message", message)
                )
                .toString(true)
        )
            .setMimeType("application/json; charset=utf-8");
    }

}
