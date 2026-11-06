package de.tiktak.kmm.plugin.fanarttv;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Small HTTP server that serves fixed fanart.tv responses and records the requests.
 */
final class FakeFanartTvServer implements AutoCloseable {

    private static final int ANY_FREE_PORT = 0;
    private static final int NOT_FOUND = 404;

    private final HttpServer server;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final List<HttpExchangeRecord> requests = new CopyOnWriteArrayList<>();

    FakeFanartTvServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", ANY_FREE_PORT), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3");
    }

    FakeFanartTvServer respond(String path, String fixture) {
        responses.put(path, new Response(200, read(fixture)));
        return this;
    }

    FakeFanartTvServer respondWithStatus(String path, int status) {
        responses.put(path, new Response(status, "{}"));
        return this;
    }

    List<HttpExchangeRecord> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        requests.add(new HttpExchangeRecord(uri.getPath(), uri.getRawQuery(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("User-Agent")));
        Response response = responses.getOrDefault(uri.getPath(), new Response(NOT_FOUND, "{}"));
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String read(String fixture) {
        try (InputStream input = FakeFanartTvServer.class.getResourceAsStream("/fanarttv/" + fixture)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Response(int status, String body) {
    }

    /** Recorded request. */
    record HttpExchangeRecord(String path, String query, String authorization, String userAgent) {
    }
}
