package de.tiktak.kmm.plugin.televisiontunes;

import java.io.IOException;
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
 * Kleiner HTTP-Server mit festen Antworten von TelevisionTunes; schreibt die angefragten Pfade mit.
 */
final class FakeTelevisionTunesServer implements AutoCloseable {

    private static final int ANY_FREE_PORT = 0;
    private static final int NOT_FOUND = 404;

    private final HttpServer server;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();

    FakeTelevisionTunesServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", ANY_FREE_PORT), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** @param path Pfad samt Anfrage, z. B. {@code /search.php?q=ALF} */
    FakeTelevisionTunesServer page(String path, String html) {
        responses.put(path, new Response(200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    FakeTelevisionTunesServer file(String path, String contentType, byte[] content) {
        responses.put(path, new Response(200, contentType, content));
        return this;
    }

    List<String> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String key = uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
        requests.add(key);
        Response response = responses.getOrDefault(key, new Response(NOT_FOUND, "text/html", new byte[0]));
        exchange.getResponseHeaders().add("Content-Type", response.contentType());
        exchange.sendResponseHeaders(response.status(), response.body().length == 0 ? -1 : response.body().length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response.body());
        }
    }

    private record Response(int status, String contentType, byte[] body) {
    }
}
