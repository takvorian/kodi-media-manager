package de.tiktak.kmm.plugin.tvdb;

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
 * Kleiner HTTP-Server, der feste TVDB-Antworten ausliefert und die Anfragen mitschreibt. Antworten
 * werden über Pfad und optional die Seite ({@code page}) zugeordnet; {@code /login} liefert ein Token.
 */
final class FakeTvdbServer implements AutoCloseable {

    static final String TOKEN = "test-token";
    private static final int ANY_FREE_PORT = 0;
    private static final int NOT_FOUND = 404;

    private final HttpServer server;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    FakeTvdbServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", ANY_FREE_PORT), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
        responses.put("/v4/login", new Response(200, "{\"status\": \"success\", \"data\": {\"token\": \"" + TOKEN + "\"}}"));
    }

    URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v4");
    }

    FakeTvdbServer respond(String path, String fixture) {
        responses.put(path, new Response(200, read(fixture)));
        return this;
    }

    FakeTvdbServer respondWithStatus(String path, int status) {
        responses.put(path, new Response(status, "{}"));
        return this;
    }

    List<Request> requests() {
        return requests;
    }

    long requestsTo(String path) {
        return requests.stream().filter(request -> request.path().equals(path)).count();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(exchange.getRequestMethod(), uri.getPath(), uri.getRawQuery(), body,
                exchange.getRequestHeaders().getFirst("Authorization")));
        String page = uri.getRawQuery() != null && uri.getRawQuery().contains("page=1") ? "?page=1" : "";
        Response response = responses.getOrDefault(uri.getPath() + page,
                responses.getOrDefault(uri.getPath(), new Response(NOT_FOUND, "{}")));
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String read(String fixture) {
        try (InputStream input = FakeTvdbServer.class.getResourceAsStream("/tvdb/" + fixture)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Response(int status, String body) {
    }

    /** Mitgeschriebene Anfrage. */
    record Request(String method, String path, String query, String body, String authorization) {
    }
}
