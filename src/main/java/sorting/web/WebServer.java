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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Local web server for the sorting animation (binds to 127.0.0.1 only).
 * <ul>
 *   <li>GET  /            static page (index.html, app.js, style.css from the classpath /web/)</li>
 *   <li>GET  /api/meta    algorithms and limits</li>
 *   <li>POST /api/run     runs the REAL multithreaded sort with tracing and returns the trace JSON</li>
 *   <li>POST /api/live/start, /api/live/step, /api/live/stop, GET /api/live/state
 *       lockstep session: the real threads wait on a semaphore permit per operation</li>
 * </ul>
 * The browser only replays events that the Java threads actually produced.
 */
public final class WebServer {
    /** Largest n the animation accepts (bars stay readable). */
    public static final int REPLAY_MAX_N = 300;
    /** Largest n for lockstep sessions (every step is one HTTP round trip). */
    public static final int LIVE_MAX_N = 100;
    private static final int MAX_SESSIONS = 4;
    private static final long SESSION_IDLE_MS = 10 * 60 * 1000;
    private static final int MAX_STEP = 1000;
    private static final int MAX_BODY = 1 << 20;
    /** Untraced timing: warm-up runs, then the median of REPS measured runs. */
    static final int WARMUPS = 3;
    static final int REPS = 5;

    private final int port;
    private HttpServer server;
    private ExecutorService pool;
    private final Map<String, LiveSession> sessions = new ConcurrentHashMap<>();
    /** Why a session was closed by the server (id -> message); only the most recent ones are kept. */
    private final Map<String, String> closedSessions = Collections.synchronizedMap(
            new LinkedHashMap<String, String>() {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CLOSED_REMEMBERED;
                }
            });
    private ScheduledExecutorService janitor;

    private static final int MAX_CLOSED_REMEMBERED = 64;
    static final String MSG_CLOSED_BY_NEWER = "Энэ Lockstep session хаагдсан: өөр цонх эсвэл tab дээр шинэ session эхэлсэн"
            + " бөгөөд зэрэг хамгийн ихдээ " + MAX_SESSIONS + " session ажиллана (хамгийн удаан идэвхгүй байсныг нь хаадаг)."
            + " \"Ажиллуулах\"-ыг дахин дарна уу.";
    static final String MSG_CLOSED_IDLE = "Энэ Lockstep session " + (SESSION_IDLE_MS / 60_000)
            + " минут идэвхгүй байсан тул автоматаар хаагдсан. \"Ажиллуулах\"-ыг дахин дарна уу.";

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
        server.createContext("/api/live/start", ex -> safe(ex, this::liveStart));
        server.createContext("/api/live/step", ex -> safe(ex, this::liveStep));
        server.createContext("/api/live/state", ex -> safe(ex, this::liveState));
        server.createContext("/api/live/stop", ex -> safe(ex, this::liveStop));
        server.createContext("/", ex -> safe(ex, this::staticFile));
        server.start();
        janitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-janitor");
            t.setDaemon(true);
            return t;
        });
        janitor.scheduleAtFixedRate(this::dropIdleSessions, 1, 1, TimeUnit.MINUTES);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        for (LiveSession ls : sessions.values()) ls.cancel();
        sessions.clear();
        if (janitor != null) janitor.shutdownNow();
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
          .append(",\"liveMaxN\":").append(LIVE_MAX_N)
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
        RunRequest req = RunRequest.parse(readForm(ex), REPLAY_MAX_N, "Replay горимд");

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

    // ------------------------------------------------------------------ lockstep

    private void liveStart(HttpExchange ex) throws IOException {
        if (!requirePost(ex)) return;
        RunRequest req = RunRequest.parse(readForm(ex), LIVE_MAX_N, "Lockstep горимд");
        List<String> notes = new ArrayList<>(req.notes);
        // At most MAX_SESSIONS sessions run at the same time. When the limit is reached the
        // session that has been idle the longest (usually a forgotten tab) is closed, and the
        // reason is remembered so that the closed session gets a clear message.
        while (sessions.size() >= MAX_SESSIONS) {
            sessions.values().stream().min((a, b) -> Long.compare(b.idleMillis(), a.idleMillis()))
                    .ifPresent(old -> {
                        old.cancel();
                        sessions.remove(old.id);
                        closedSessions.put(old.id, MSG_CLOSED_BY_NEWER);
                        notes.add("Тэмдэглэл: зэрэг ажиллах Lockstep session-ий хязгаар (" + MAX_SESSIONS
                                + ")-т хүрсэн тул хамгийн удаан идэвхгүй байсан session-ийг хаалаа.");
                    });
        }
        LiveSession ls = new LiveSession(req.values, req.left, req.right, req.source, notes);
        sessions.put(ls.id, ls);
        ls.start();
        sendJson(ex, 200, ls.startJson());
    }

    private void liveStep(HttpExchange ex) throws Exception {
        if (!requirePost(ex)) return;
        Map<String, String> f = readForm(ex);
        LiveSession ls = session(f);
        long count = InputParser.parseOptionLong("count", f.getOrDefault("count", "1"));
        if (count < 1 || count > MAX_STEP) throw new InputException("count 1.." + MAX_STEP + " мужид байх ёстой.");
        ls.step((int) count, 2000);
        sendJson(ex, 200, ls.stateJson(from(f)));
    }

    private void liveState(HttpExchange ex) throws IOException {
        Map<String, String> f = readForm(ex);
        sendJson(ex, 200, session(f).stateJson(from(f)));
    }

    private void liveStop(HttpExchange ex) throws IOException {
        if (!requirePost(ex)) return;
        Map<String, String> f = readForm(ex);
        LiveSession ls = sessions.remove(f.getOrDefault("id", ""));
        if (ls != null) ls.cancel();
        sendJson(ex, 200, "{\"ok\":true}");
    }

    private LiveSession session(Map<String, String> f) {
        String id = f.getOrDefault("id", "");
        LiveSession ls = sessions.get(id);
        if (ls == null) {
            String reason = closedSessions.get(id);
            throw new InputException(reason != null ? reason
                    : "Lockstep session олдсонгүй (session хаагдсан эсвэл сервер дахин асаасан). \"Ажиллуулах\"-ыг дахин дарна уу.");
        }
        return ls;
    }

    private static int from(Map<String, String> f) {
        long v = InputParser.parseOptionLong("from", f.getOrDefault("from", "0"));
        return (int) Math.max(0, Math.min(v, Integer.MAX_VALUE));
    }

    private static boolean requirePost(HttpExchange ex) throws IOException {
        if ("POST".equals(ex.getRequestMethod())) return true;
        sendJson(ex, 405, "{\"ok\":false,\"error\":\"POST шаардлагатай\"}");
        return false;
    }

    private void dropIdleSessions() {
        for (LiveSession ls : sessions.values()) {
            if (ls.idleMillis() > SESSION_IDLE_MS) {
                ls.cancel();
                sessions.remove(ls.id);
                closedSessions.put(ls.id, MSG_CLOSED_IDLE);
            }
        }
    }

    private void staticFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        if (path.equals("/favicon.ico")) {
            // the page declares an empty icon; answer browsers that still ask for one without a 404
            ex.sendResponseHeaders(204, -1);
            return;
        }
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

        static RunRequest parse(Map<String, String> f, int maxN, String modeLabel) {
            RunRequest r = new RunRequest();
            r.left = algo(f.getOrDefault("algoLeft", "merge"));
            r.right = algo(f.getOrDefault("algoRight", "merge"));
            String mode = f.getOrDefault("source", "input");
            if (mode.equals("random")) {
                long n = InputParser.parseOptionLong("n (хэмжээ)", f.getOrDefault("n", ""));
                if (n > maxN) throw new InputException(modeLabel + " n ≤ " + maxN + " (одоо n = " + n + ").");
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
                throw new InputException(modeLabel + " n ≤ " + maxN + " (одоо n = " + r.values.length
                        + "). Том өгөгдлийг console горимоор ажиллуулна уу: ./run.sh --random N --algo merge");
            }
            r.notes = new ArrayList<>(mode.equals("random") ? List.of() : InputParser.emptyFieldNotes(f.get("input")));
            r.notes.addAll(Limits.check(r.values.length, r.left, r.right, false, true));
            return r;
        }

        private static Algorithm algo(String key) {
            Algorithm a = Algorithm.fromKey(key);
            if (a == null) throw new InputException("'" + key + "' гэсэн алгоритм байхгүй. Боломжит: " + Algorithm.keysList());
            return a;
        }
    }
}
