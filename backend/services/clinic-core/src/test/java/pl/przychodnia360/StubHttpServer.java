package pl.przychodnia360;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/** Atrapa usługi HTTP do testów zdalnych fasad: zapisuje żądania i odpowiada tym, co zwróci zachowanie testu. */
public final class StubHttpServer implements AutoCloseable {

    /** Odpowiedź atrapy: status, treść JSON i opóźnienie (do testu limitu czasu). */
    public record Reply(int status, String body, long delayMillis) {
        public static Reply ok(String body) {
            return new Reply(200, body, 0);
        }

        public static Reply status(int status) {
            return new Reply(status, "{}", 0);
        }

        public static Reply slow(String body, long delayMillis) {
            return new Reply(200, body, delayMillis);
        }
    }

    /** Odebrane żądanie. */
    public record Received(String method, String path, String query, String authorization, String body) {
    }

    private final HttpServer server;
    private final List<Received> received = Collections.synchronizedList(new ArrayList<>());
    private volatile Function<Received, Reply> behaviour = r -> Reply.ok("[]");

    public StubHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", exchange -> {
            String requestBody;
            try {
                requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                requestBody = "";
            }
            Received request = new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst("Authorization"),
                    requestBody);
            received.add(request);
            Reply reply = behaviour.apply(request);
            try {
                if (reply.delayMillis() > 0) {
                    Thread.sleep(reply.delayMillis());
                }
                byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException | IOException ignored) {
                // klient mógł zrezygnować (limit czasu)
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    public StubHttpServer respond(Function<Received, Reply> behaviour) {
        this.behaviour = behaviour;
        return this;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Received> received() {
        return received;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
