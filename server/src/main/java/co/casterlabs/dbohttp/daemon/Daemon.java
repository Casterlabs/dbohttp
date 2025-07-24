package co.casterlabs.dbohttp.daemon;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.auth0.jwt.exceptions.JWTVerificationException;

import co.casterlabs.dbohttp.DBOHTTP;
import co.casterlabs.dbohttp.database.QueryException;
import co.casterlabs.dbohttp.database.QueryResult;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonNull;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rhs.protocol.HttpMethod;
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
        HttpServerBuilder builder = new HttpServerBuilder()
            .withBehindProxy(true)
            .withPort(port);

        this.server = builder.build(this);
    }

    private HttpResponse handleQuery(HttpSession session) {
        QueryRequestBody request;

        try {
            String contentType = session.getHeader("Content-Type");
            if (contentType == null) contentType = "text/plain";

            switch (contentType.toLowerCase().split(";")[0]) {
                case "application/json":
                    request = Rson.DEFAULT.fromJson(session.getRequestBody(), QueryRequestBody.class);
                    break;

                case "text/plain": {
                    String sql = session.getRequestBody();

                    JsonArray params = new JsonArray();
                    for (int i = 0; i < session.getQueryParameters().size(); i++) {
                        params.add(JsonNull.INSTANCE);
                    }
                    for (Map.Entry<String, String> param : session.getQueryParameters().entrySet()) {
                        int index = Integer.parseInt(param.getKey());
                        JsonElement je = Rson.DEFAULT.fromJson(param.getValue(), JsonElement.class);

                        params.set(index, je);
                    }

                    request = new QueryRequestBody(sql, params);
                    break;
                }

                default:
                    throw new IllegalArgumentException();
            }
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
            long start_ns = System.nanoTime();
            QueryResult result = DBOHTTP.database.query(request, request.sql, request.params);

            // Build the response object .
            JsonObject profile = result.profiler().toJson();
            JsonObject meta = new JsonObject()
                .put("profile", profile)
//                .put("took", took_ms)
                .put("rowsReturned", result.rows().size());

            JsonObject response = new JsonObject()
                .put("results", result.rowsJson())
                .put("meta", meta)
                .putNull("error");

            // One last profile...
            double took_ms = (System.nanoTime() - start_ns) / 1000000d;
            meta.put("took", took_ms);
            profile.put("Miscellaneous", took_ms - result.profiler().timeSpent_ms);

            // Okay, we're done. Off to RHS you go.
            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.OK, response.toString(false))
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
                            .put("report", DBOHTTP.database.generateReport())
                            .put("tables", Rson.DEFAULT.toJson(DBOHTTP.database.listTables()))
                    )
                    .putNull("error")
                    .toString(false)
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
        try {
            String origin = session.getHeader("Origin");
            if (origin == null) origin = "*";

            if (session.getMethod() == HttpMethod.OPTIONS) {
                return HttpResponse
                    .newFixedLengthResponse(StandardHttpStatus.NO_CONTENT)
                    .putHeader("Access-Control-Allow-Origin", origin)
                    .putHeader("Access-Control-Allow-Methods", "OPTIONS, GET, POST")
                    .putHeader("Access-Control-Max-Age", "86400")
                    .putHeader("Access-Control-Allow-Headers", "Authorization, *");
            }

            String token = session.getHeader("Authorization");
            if (token == null) throw new IllegalAccessException();

            if (!token.startsWith("Bearer ")) throw new IllegalAccessException();
            token = token.substring("Bearer ".length());

            HttpResponse response;

            switch (session.getMethod()) {
                case GET:
                    DBOHTTP.infoVerifier.verify(token); // Check it.
                    response = this.handleInfo(session);
                    break;

                case POST:
                    DBOHTTP.queryVerifier.verify(token); // Check it.
                    response = this.handleQuery(session);
                    break;

                default:
                    return NOT_IMPLEMENTED;
            }

            return response
                .putHeader("Access-Control-Allow-Origin", origin)
                .putHeader("Access-Control-Allow-Methods", "OPTIONS, GET, POST")
                .putHeader("Access-Control-Max-Age", "86400")
                .putHeader("Access-Control-Allow-Headers", "Authorization, *");
        } catch (JWTVerificationException | IllegalAccessException e) {
            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.UNAUTHORIZED);
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
                .toString(false)
        )
            .setMimeType("application/json; charset=utf-8");
    }

}
