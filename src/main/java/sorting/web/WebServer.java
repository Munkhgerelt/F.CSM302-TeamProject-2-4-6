package sorting.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import sorting.algo.Algorithm;
import sorting.core.MultithreadedSorter;
import sorting.core.SortResult;
import sorting.input.DataGenerator;
import sorting.input.InputException;
import sorting.input.InputParser;
import sorting.input.Limits;
import sorting.trace.Json;
import sorting.trace.TraceJson;
import sorting.trace.TraceRecorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local web server for the sorting animation (binds to 127.0.0.1 only).
 * <ul>
 *   <li>GET  /            static page (index.html, app.js, style.css from the classpath /web/)</li>
 *   <li>GET  /api/meta    algorithms and limits</li>
 *   <li>POST /api/run     runs the REAL multithreaded sort with tracing and returns the trace JSON</li>
 * </ul>
 * The browser only replays events that the Java threads actually produced.
 */
public final class WebServer {
    /** Largest n the animation accepts (bars stay readable). */
    public static final int REPLAY_MAX_N = 300;
    private static final int MAX_BODY = 1 << 20;
    /** Untraced timing: warm-up runs, then the median of REPS measured runs. */
    static final int WARMUPS = 3;
    static final int REPS = 5;

    private final int port;
    private HttpServer server;
    private ExecutorService pool;

    public WebServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (BindException e) {
            throw new IOException(port + " порт аль хэдийн ашиглагдаж байна. Өөр порт сонгоно уу, жишээ нь: ./run.sh --web --port "
                    + (port + 1), e);
        }
        pool = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "http-worker");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(pool);
        server.createContext("/api/meta", ex -> safe(ex, this::meta));
        server.createContext("/api/run", ex -> safe(ex, this::run));
        server.createContext("/", ex -> safe(ex, this::staticFile));
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    // ------------------------------------------------------------------ handlers

    interface Handler {
        void handle(HttpExchange ex) throws Exception;
    }

    private static void safe(HttpExchange ex, Handler h) throws IOException {
        try {
            h.handle(ex);
        } catch (InputException e) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":" + Json.str(e.getMessage()) + "}");
        } catch (Exception e) {
            sendJson(ex, 500, "{\"ok\":false,\"error\":" + Json.str("Серверийн дотоод алдаа: " + e) + "}");
        } finally {
            ex.close();
        }
    }

    private void meta(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"algorithms\":[");
        Algorithm[] all = Algorithm.values();
        for (int i = 0; i < all.length; i++) {
            Algorithm a = all[i];
            if (i > 0) sb.append(',');
            sb.append("{\"key\":").append(Json.str(a.key))
              .append(",\"name\":").append(Json.str(a.displayName))
              .append(",\"complexity\":").append(Json.str(a.complexity)).append('}');
        }
        sb.append("],\"limits\":{\"replayMaxN\":").append(REPLAY_MAX_N)
          .append(",\"quadraticMaxN\":").append(Algorithm.MAX_N_QUADRATIC)
          .append(",\"hardMaxN\":").append(Limits.HARD_MAX_N)
          .append("},\"defaults\":{\"min\":").append(DataGenerator.DEFAULT_MIN)
          .append(",\"max\":").append(DataGenerator.DEFAULT_MAX).append("}}");
        sendJson(ex, 200, sb.toString());
    }

    private void run(HttpExchange ex) throws Exception {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"POST шаардлагатай\"}");
            return;
        }
        RunRequest req = RunRequest.parse(readForm(ex), REPLAY_MAX_N);

        // 1) honest reference time without tracing (warm-up + median), 2) traced run for the animation
        long untraced = MultithreadedSorter.medianUntracedNanos(req.values,
                req.left.key, req.left::create, req.right.key, req.right::create, WARMUPS, REPS);
        TraceRecorder rec = new TraceRecorder(System.nanoTime());
        SortResult traced = MultithreadedSorter.sort(req.values,
                req.left.key, req.left.create(), req.right.key, req.right.create(), rec);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":true,\"source\":").append(Json.str(req.source)).append(",\"notes\":[");
        for (int i = 0; i < req.notes.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(Json.str(req.notes.get(i)));
        }
        sb.append("],\"trace\":").append(TraceJson.toJson(traced, rec, untraced, REPS)).append('}');
        sendJson(ex, 200, sb.toString());
    }

    private void staticFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..") || !path.matches("/[A-Za-z0-9._-]+")) {
            sendText(ex, 404, "text/plain", "Not found");
            return;
        }
        try (InputStream in = WebServer.class.getResourceAsStream("/web" + path)) {
            if (in == null) {
                sendText(ex, 404, "text/plain", "Not found: " + path
                        + (path.equals("/index.html") ? " (out/web/ хавтас алга. ./build.sh-ийг дахин ажиллуулна уу.)" : ""));
                return;
            }
            byte[] body = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", contentType(path));
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    static Map<String, String> readForm(HttpExchange ex) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = ex.getRequestBody()) {
            byte[] chunk = new byte[8192];
            int r;
            while ((r = in.read(chunk)) != -1) {
                buf.write(chunk, 0, r);
                if (buf.size() > MAX_BODY) throw new InputException("Хүсэлт хэт том (1 MB-аас их).");
            }
        }
        Map<String, String> form = new HashMap<>();
        String body = buf.toString(StandardCharsets.UTF_8);
        String q = ex.getRequestURI().getRawQuery();
        if (q != null && !q.isEmpty()) body = body.isEmpty() ? q : body + "&" + q;
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            form.put(k, v);
        }
        return form;
    }

    static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        sendText(ex, status, "application/json; charset=utf-8", json);
    }

    static void sendText(HttpExchange ex, int status, String type, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    /** Validated parameters of one run request from the browser. */
    static final class RunRequest {
        int[] values;
        Algorithm left;
        Algorithm right;
        String source;
        List<String> notes;

        static RunRequest parse(Map<String, String> f, int maxN) {
            RunRequest r = new RunRequest();
            r.left = algo(f.getOrDefault("algoLeft", "merge"));
            r.right = algo(f.getOrDefault("algoRight", "merge"));
            String mode = f.getOrDefault("source", "input");
            if (mode.equals("random")) {
                long n = InputParser.parseOptionLong("n (хэмжээ)", f.getOrDefault("n", ""));
                if (n > maxN) throw new InputException("Animation-д n ≤ " + maxN + " (одоо n = " + n + ").");
                String seedText = f.getOrDefault("seed", "").trim();
                long seed = seedText.isEmpty() ? System.currentTimeMillis() : InputParser.parseOptionLong("seed", seedText);
                long min = InputParser.parseOptionLong("min", f.getOrDefault("min", String.valueOf(DataGenerator.DEFAULT_MIN)));
                long max = InputParser.parseOptionLong("max", f.getOrDefault("max", String.valueOf(DataGenerator.DEFAULT_MAX)));
                DataGenerator.Pattern p = DataGenerator.Pattern.fromKey(f.getOrDefault("pattern", "random"));
                r.values = DataGenerator.generate(n, seed, min, max, p);
                r.source = "random n=" + n + " seed=" + seed + " муж=[" + min + "," + max + "] хэлбэр=" + p.key;
            } else {
                r.values = InputParser.parseList(f.get("input"));
                r.source = "гараас оруулсан";
            }
            if (r.values.length > maxN) {
                throw new InputException("Animation-д n ≤ " + maxN + " (одоо n = " + r.values.length
                        + "). Том өгөгдлийг console горимоор ажиллуулна уу: ./run.sh --random N --algo merge");
            }
            r.notes = Limits.check(r.values.length, r.left, r.right, false, true);
            return r;
        }

        private static Algorithm algo(String key) {
            Algorithm a = Algorithm.fromKey(key);
            if (a == null) throw new InputException("'" + key + "' гэсэн алгоритм байхгүй. Боломжит: " + Algorithm.keysList());
            return a;
        }
    }
}
