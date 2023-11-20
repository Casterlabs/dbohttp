package co.casterlabs.dbohttp;

import java.io.Closeable;
import java.io.IOException;

import org.jetbrains.annotations.Nullable;

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
    private HttpServer server;

    public Daemon(int port) {
        this.server = new HttpServerBuilder()
            .setBehindProxy(true)
            .setPort(port)
            .build(this);
    }

    private HttpResponse handleQuery(HttpSession session) {
        return null;
    }

    @Override
    public @Nullable HttpResponse serveHttpSession(HttpSession session) {
        try {
            switch (session.getUri()) {
                case "query":
                    return this.handleQuery(session);

                default:
                    return null; // Not Implemented
            }
        } catch (Throwable t) {
            FastLogger.logStatic(LogLevel.SEVERE, t);
            return HttpResponse.INTERNAL_ERROR;
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

}
