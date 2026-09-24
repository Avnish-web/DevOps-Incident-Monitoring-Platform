package dev.monitoring.worker.check;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local HTTP server (JDK built-in) with endpoints that simulate target behaviours. */
public class TestHttpServer implements AutoCloseable {

    public record Received(String method, String path, String userAgent,
                           Map<String, String> headers, String body) {
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    public final List<Received> received = new CopyOnWriteArrayList<>();

    public TestHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        Map<String, String> headers = new HashMap<>();
        ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(new Received(ex.getRequestMethod(), path,
                ex.getRequestHeaders().getFirst("User-Agent"), headers, body));
        try {
            if (path.equals("/hook")) {
                respond(ex, 204, null);
            } else if (path.equals("/hook-fail")) {
                respond(ex, 500, "receiver error");
            } else if (path.equals("/hook-redirect")) {
                redirect(ex, "/hook");
            } else if (path.equals("/ok")) {
                respond(ex, 200, "ok");
            } else if (path.startsWith("/status/")) {
                int code = Integer.parseInt(path.substring("/status/".length()));
                respond(ex, code, code == 204 ? null : "status");
            } else if (path.equals("/redirect")) {
                // /redirect?to=<url-encoded absolute or relative URL>
                String query = ex.getRequestURI().getRawQuery();
                redirect(ex, URLDecoder.decode(query.substring("to=".length()),
                        StandardCharsets.UTF_8));
            } else if (path.startsWith("/chain/")) {
                int remaining = Integer.parseInt(path.substring("/chain/".length()));
                if (remaining == 0) {
                    respond(ex, 200, "end");
                } else {
                    redirect(ex, "/chain/" + (remaining - 1));
                }
            } else if (path.equals("/loop")) {
                redirect(ex, "/loop");
            } else if (path.equals("/slow-headers")) {
                sleep(3000);
                respond(ex, 200, "late");
            } else if (path.equals("/drip")) {
                ex.sendResponseHeaders(200, 0);
                try (OutputStream out = ex.getResponseBody()) {
                    for (int i = 0; i < 50; i++) {
                        out.write('x');
                        out.flush();
                        sleep(100);
                    }
                }
            } else if (path.equals("/big")) {
                byte[] chunk = new byte[64 * 1024];
                ex.sendResponseHeaders(200, 0);
                try (OutputStream out = ex.getResponseBody()) {
                    for (int i = 0; i < 80; i++) { // 5 MB
                        out.write(chunk);
                    }
                }
            } else {
                respond(ex, 404, "not found");
            }
        } catch (IOException e) {
            // client went away (timeouts, capped reads): expected in these tests
        } finally {
            ex.close();
        }
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        if (body == null || ex.getRequestMethod().equals("HEAD")) {
            ex.sendResponseHeaders(code, -1);
            return;
        }
        byte[] bytes = body.getBytes();
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().add("Location", location);
        ex.sendResponseHeaders(302, -1);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
