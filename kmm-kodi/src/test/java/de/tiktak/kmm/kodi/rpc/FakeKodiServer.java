package de.tiktak.kmm.kodi.rpc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kleiner HTTP-Server, der wie Kodi JSON-RPC-Aufrufe unter {@code /jsonrpc} beantwortet und sie
 * mitschreibt. Unbekannte Methoden beantwortet er mit {@code "OK"}.
 */
public final class FakeKodiServer implements AutoCloseable {

    private static final int ANY_FREE_PORT = 0;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpServer server;
    private final Map<String, String> results = new ConcurrentHashMap<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String rawBody;

    public FakeKodiServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", ANY_FREE_PORT), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/jsonrpc", this::handle);
        server.start();
    }

    public KodiEndpoint endpoint() {
        return endpoint(null, null);
    }

    public KodiEndpoint endpoint(String username, String password) {
        return new KodiEndpoint("127.0.0.1", port(), username, password);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /** Antwortet auf die Methode mit diesem JSON als {@code result}. */
    public FakeKodiServer respond(String method, String resultJson) {
        results.put(method, resultJson);
        return this;
    }

    /** Antwortet auf die Methode mit einem JSON-RPC-Fehler. */
    public FakeKodiServer fail(String method, int code, String message) {
        results.put(method, "!" + JSON.writeValueAsString(Map.of("code", code, "message", message)));
        return this;
    }

    public FakeKodiServer respondWithStatus(int httpStatus) {
        this.status = httpStatus;
        return this;
    }

    public FakeKodiServer respondWithBody(String body) {
        this.rawBody = body;
        return this;
    }

    public List<Call> calls() {
        return calls;
    }

    public List<String> methods() {
        return calls.stream().map(Call::method).toList();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        JsonNode request = JSON.readTree(exchange.getRequestBody().readAllBytes());
        String method = request.path("method").asString();
        calls.add(new Call(method, request.path("params"), exchange.getRequestHeaders().getFirst("Authorization")));
        String body = rawBody != null ? rawBody : response(request, method);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private String response(JsonNode request, String method) {
        String result = results.getOrDefault(method, "\"OK\"");
        String id = request.path("id").toString();
        if (result.startsWith("!")) {
            return "{\"jsonrpc\":\"2.0\",\"id\":%s,\"error\":%s}".formatted(id, result.substring(1));
        }
        return "{\"jsonrpc\":\"2.0\",\"id\":%s,\"result\":%s}".formatted(id, result);
    }

    /** Mitgeschriebener Aufruf. */
    public record Call(String method, JsonNode params, String authorization) {
    }
}
