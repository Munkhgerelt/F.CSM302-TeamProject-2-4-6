import sorting.algo.Algorithm;
import sorting.core.MultithreadedSorter;
import sorting.core.RunOptions;
import sorting.core.SortResult;
import sorting.core.StepGate;
import sorting.input.DataGenerator;
import sorting.input.InputException;
import sorting.input.InputParser;
import sorting.input.Limits;
import sorting.trace.TraceEvent;
import sorting.trace.TraceJson;
import sorting.trace.TraceRecorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Automated tests for the II-4-6 Multithreaded Sorting Application.
 * No JUnit: plain Java 17, run with ./test.sh (or test.bat).
 * Every check is compared against java.util.Arrays.sort and the outcome of each
 * test case (expected vs. actual) is written to tests/results/test-results.md.
 *
 * Exit code: 0 when all tests pass, 1 otherwise.
 */
public final class TestRunner {

    private static final String BOOK = "7, 12, 19, 3, 18, 4, 2, 6, 15, 8";
    private static final String BOOK_SORTED = "2 3 4 6 7 8 12 15 18 19";

    /** One row of the result table. */
    private static final class Row {
        final int no;
        final String category;
        final String name;
        final String input;
        final String expected;
        final String actual;
        final boolean pass;
        final long millis;

        Row(int no, String category, String name, String input, String expected, String actual, boolean pass, long millis) {
            this.no = no;
            this.category = category;
            this.name = name;
            this.input = input;
            this.expected = expected;
            this.actual = actual;
            this.pass = pass;
            this.millis = millis;
        }
    }

    /** Outcome produced by a test body. */
    private static final class Outcome {
        final String actual;
        final boolean pass;

        Outcome(String actual, boolean pass) {
            this.actual = actual;
            this.pass = pass;
        }
    }

    @FunctionalInterface
    private interface Body {
        Outcome run() throws Exception;
    }

    private static final List<Row> ROWS = new ArrayList<>();
    private static String currentCategory = "";
    private static Path root;

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();

        bookTests();
        boundaryTests();
        errorTests();
        duplicateAndPatternTests();
        largeTests();
        threadTests();
        traceTests();
        lockstepTests();
        cliTests();

        long passed = ROWS.stream().filter(r -> r.pass).count();
        System.out.println();
        System.out.println("Нийт: " + ROWS.size() + ", амжилттай: " + passed + ", алдаатай: " + (ROWS.size() - passed));
        Path report = writeMarkdown(passed);
        System.out.println("Хүснэгт: " + root.relativize(report));
        System.exit(passed == ROWS.size() ? 0 : 1);
    }

    // =====================================================================
    // 1. Textbook example
    // =====================================================================
    private static void bookTests() {
        category("Номын жишээ");
        for (Algorithm l : Algorithm.values()) {
            for (Algorithm r : Algorithm.values()) {
                check("Номын жишээ, " + l.key + " + " + r.key, BOOK, BOOK_SORTED,
                        () -> sortedOutcome(InputParser.parseList(BOOK), l, r));
            }
        }
        check("Хуваалт: mid ба хоёр хэсэг", BOOK,
                "mid=5; зүүн [0,5) → 3 7 12 18 19; баруун [5,10) → 2 4 6 8 15",
                () -> {
                    int[] in = InputParser.parseList(BOOK);
                    TraceRecorder rec = new TraceRecorder(System.nanoTime());
                    SortResult r = MultithreadedSorter.sort(in, "merge", Algorithm.MERGE.create(),
                            "merge", Algorithm.MERGE.create(), rec);
                    int[] list = replayList(in, rec.snapshot());
                    String actual = "mid=" + r.mid + "; зүүн [0," + r.mid + ") → " + join(Arrays.copyOfRange(list, 0, r.mid))
                            + "; баруун [" + r.mid + "," + in.length + ") → " + join(Arrays.copyOfRange(list, r.mid, in.length));
                    return new Outcome(actual, actual.equals("mid=5; зүүн [0,5) → 3 7 12 18 19; баруун [5,10) → 2 4 6 8 15"));
                });
    }

    // =====================================================================
    // 2. Boundary cases
    // =====================================================================
    private static void boundaryTests() {
        category("Захын тохиолдол");
        sortCase("n = 1", "42", "42");
        sortCase("n = 2, эрэмбэтэй", "1, 2", "1 2");
        sortCase("n = 2, урвуу", "2, 1", "1 2");
        sortCase("n = 3 (сондгой)", "3, 1, 2", "1 2 3");
        sortCase("Бүгд ижил", "5, 5, 5, 5, 5, 5, 5", "5 5 5 5 5 5 5");
        sortCase("Сөрөг тоо", "-3, 0, -10, 7, -1", "-10 -3 -1 0 7");
        sortCase("int-ийн хязгаар", "2147483647, -2147483648, 0, -1, 2147483647",
                "-2147483648 -1 0 2147483647 2147483647");
        sortCase("Эрэмбэтэй", "1, 2, 3, 4, 5, 6, 7, 8", "1 2 3 4 5 6 7 8");
        sortCase("Урвуу", "8, 7, 6, 5, 4, 3, 2, 1", "1 2 3 4 5 6 7 8");
        for (int n : new int[] {1, 2, 3, 4, 5, 10, 11}) {
            String expected = "зүүн " + ((n + 1) / 2) + ", баруун " + (n / 2);
            check("Хуваалтын хэмжээ n = " + n, "n=" + n, expected, () -> {
                int[] in = DataGenerator.generate(n, 1, 1, 100, DataGenerator.Pattern.RANDOM);
                SortResult r = MultithreadedSorter.sort(in, "insertion", Algorithm.INSERTION.create(),
                        "insertion", Algorithm.INSERTION.create());
                String actual = "зүүн " + r.mid + ", баруун " + (n - r.mid);
                return new Outcome(actual, actual.equals(expected) && isSortedCopyOf(in, r.result));
            });
        }
        check("Сондгой n-ийн тэмдэглэл", "n=11", "⌈n/2⌉ / ⌊n/2⌋ тэмдэглэл гарна", () -> {
            List<String> w = Limits.check(11, Algorithm.MERGE, Algorithm.MERGE, false);
            boolean ok = w.stream().anyMatch(s -> s.contains("зүүн хэсэг 6") && s.contains("баруун хэсэг 5"));
            return new Outcome(ok ? "⌈n/2⌉ / ⌊n/2⌋ тэмдэглэл гарна" : String.join(" | ", w), ok);
        });
    }

    // =====================================================================
    // 3. Invalid input
    // =====================================================================
    private static void errorTests() {
        category("Буруу оролт");
        parseError("Хоосон оролт", "", "хоосон");
        parseError("Зөвхөн зай", "   ", "хоосон");
        parseError("Тоо биш утга", "5, 3, abc, 8", "abc");
        parseError("Бутархай тоо", "1, 2.5, 3", "2.5");
        parseError("int-ээс их", "1, 3000000000", "3000000000");
        parseError("int-ээс бага", "-2147483649", "-2147483649");
        limitError("O(n²) хязгаар (insertion, n = 20 001)", 20_001, Algorithm.INSERTION, Algorithm.MERGE, false, false,
                "20000");
        check("--force-той O(n²) n = 20 001", "n=20001, insertion, force", "анхааруулга, алдаагүй", () -> {
            List<String> w = Limits.check(20_001, Algorithm.INSERTION, Algorithm.MERGE, true);
            boolean ok = w.stream().anyMatch(s -> s.startsWith("Анхааруулга"));
            return new Outcome(ok ? "анхааруулга, алдаагүй" : w.toString(), ok);
        });
        limitError("Дээд хязгаар n = 1 000 001", 1_000_001, Algorithm.MERGE, Algorithm.QUICK, true, false, "1000000");
        limitError("Trace хязгаар O(n²), n = 1001", 1_001, Algorithm.BUBBLE, Algorithm.MERGE, false, true, "1000");
        limitError("Trace хязгаар merge, n = 10 001", 10_001, Algorithm.MERGE, Algorithm.QUICK, false, true, "10000");
        generatorError("Санамсаргүй n = 0", 0, 1, 100, "эерэг");
        generatorError("min > max", 5, 9, 1, "max");
        check("Буруу алгоритмын нэр", "foo", "танигдахгүй (null)", () -> {
            Algorithm a = Algorithm.fromKey("foo");
            return new Outcome(a == null ? "танигдахгүй (null)" : a.key, a == null);
        });
        fileError("Хоосон файл (зөвхөн тайлбар)", "examples/empty.txt");
        fileError("Тоо биш утгатай файл", "examples/invalid-letters.txt");
        fileError("int-ээс хэтэрсэн файл", "examples/invalid-overflow.txt");
        fileError("Байхгүй файл", "examples/does-not-exist.txt");
    }

    // =====================================================================
    // 4. Duplicates and input patterns
    // =====================================================================
    private static void duplicateAndPatternTests() {
        category("Давхардал ба хэлбэр");
        sortCase("Давхардалтай (жижиг)", "3, 1, 5, 3, 2, 1, 5, 3, 2, 3, 1, 5", "1 1 1 2 2 3 3 3 3 5 5 5");
        for (DataGenerator.Pattern p : DataGenerator.Pattern.values()) {
            check("Хэлбэр " + p.name().toLowerCase() + ", n = 300, бүх 25 хослол", "seed=7",
                    "25/25 зөв", () -> {
                        int[] in = DataGenerator.generate(300, 7, 1, 100, p);
                        int ok = 0;
                        for (Algorithm l : Algorithm.values()) {
                            for (Algorithm r : Algorithm.values()) {
                                SortResult res = MultithreadedSorter.sort(in.clone(), l.key, l.create(), r.key, r.create());
                                if (isSortedCopyOf(in, res.result)) ok++;
                            }
                        }
                        return new Outcome(ok + "/25 зөв", ok == 25);
                    });
        }
        check("Санамсаргүй 2000 удаагийн туршилт", "n ∈ [1, 200], алгоритм санамсаргүй", "2000/2000 зөв", () -> {
            Random rnd = new Random(2026);
            Algorithm[] all = Algorithm.values();
            int ok = 0;
            for (int i = 0; i < 2000; i++) {
                int n = 1 + rnd.nextInt(200);
                int[] in = new int[n];
                for (int k = 0; k < n; k++) in[k] = rnd.nextInt(41) - 20;
                Algorithm l = all[rnd.nextInt(all.length)];
                Algorithm r = all[rnd.nextInt(all.length)];
                SortResult res = MultithreadedSorter.sort(in, l.key, l.create(), r.key, r.create());
                if (isSortedCopyOf(in, res.result)) ok++;
            }
            return new Outcome(ok + "/2000 зөв", ok == 2000);
        });
    }

    // =====================================================================
    // 5. Large inputs
    // =====================================================================
    private static void largeTests() {
        category("Том өгөгдөл");
        large(1_000_000, Algorithm.MERGE, Algorithm.QUICK);
        large(1_000_000, Algorithm.QUICK, Algorithm.QUICK);
        large(20_000, Algorithm.INSERTION, Algorithm.BUBBLE);
        large(20_000, Algorithm.SELECTION, Algorithm.MERGE);
    }

    private static void large(int n, Algorithm l, Algorithm r) {
        check("n = " + String.format("%,d", n).replace(',', ' ') + ", " + l.key + " + " + r.key,
                "random, seed=1", "Arrays.sort-той ижил", () -> {
                    int[] in = DataGenerator.generate(n, 1, Integer.MIN_VALUE, Integer.MAX_VALUE,
                            DataGenerator.Pattern.RANDOM);
                    long t = System.nanoTime();
                    SortResult res = MultithreadedSorter.sort(in, l.key, l.create(), r.key, r.create());
                    long ms = (System.nanoTime() - t) / 1_000_000;
                    boolean ok = isSortedCopyOf(in, res.result);
                    return new Outcome((ok ? "Arrays.sort-той ижил" : "ЗӨРҮҮТЭЙ") + " (" + ms + " ms)", ok);
                });
    }

    // =====================================================================
    // 6. Thread behaviour
    // =====================================================================
    private static void threadTests() {
        category("Thread");
        check("3 тусдаа thread, нэр", "номын жишээ", "Sorter-L, Sorter-R, Merger; 3 өөр thread id", () -> {
            SortResult r = MultithreadedSorter.sort(InputParser.parseList(BOOK), "merge", Algorithm.MERGE.create(),
                    "quick", Algorithm.QUICK.create());
            long main = Thread.currentThread().getId();
            boolean distinct = r.left.getId() != r.right.getId() && r.left.getId() != r.merger.getId()
                    && r.right.getId() != r.merger.getId() && r.left.getId() != main && r.merger.getId() != main;
            String actual = r.left.getName() + ", " + r.right.getName() + ", " + r.merger.getName()
                    + (distinct ? "; 3 өөр thread id" : "; id давхцсан");
            return new Outcome(actual, actual.equals("Sorter-L, Sorter-R, Merger; 3 өөр thread id"));
        });
        check("Параметр (startIndex, endIndex)", "n = 11", "L [0,6), R [6,11), Merger [0,6)+[6,11)", () -> {
            int[] in = DataGenerator.generate(11, 3, 1, 50, DataGenerator.Pattern.RANDOM);
            SortResult r = MultithreadedSorter.sort(in, "bubble", Algorithm.BUBBLE.create(),
                    "selection", Algorithm.SELECTION.create());
            String actual = "L [" + r.left.params().startIndex + "," + r.left.params().endIndex + "), R ["
                    + r.right.params().startIndex + "," + r.right.params().endIndex + "), Merger ["
                    + r.merger.params().leftStart + "," + r.merger.params().mid + ")+[" + r.merger.params().mid + ","
                    + r.merger.params().end + ")";
            return new Outcome(actual, actual.equals("L [0,6), R [6,11), Merger [0,6)+[6,11)"));
        });
        check("Parent join хийсний дараа бүгд TERMINATED", "n = 1000", "TERMINATED ×3", () -> {
            int[] in = DataGenerator.generate(1000, 5, 1, 1000, DataGenerator.Pattern.RANDOM);
            SortResult r = MultithreadedSorter.sort(in, "insertion", Algorithm.INSERTION.create(),
                    "merge", Algorithm.MERGE.create());
            boolean ok = r.left.getState() == Thread.State.TERMINATED && r.right.getState() == Thread.State.TERMINATED
                    && r.merger.getState() == Thread.State.TERMINATED;
            return new Outcome(ok ? "TERMINATED ×3" : r.left.getState() + "/" + r.right.getState() + "/" + r.merger.getState(), ok);
        });
        check("Merger хоёр sorter дууссаны дараа эхэлнэ", "100 удаа, n = 2000", "100/100", () -> {
            int ok = 0;
            for (int i = 0; i < 100; i++) {
                int[] in = DataGenerator.generate(2000, i, 1, 10_000, DataGenerator.Pattern.RANDOM);
                SortResult r = MultithreadedSorter.sort(in, "quick", Algorithm.QUICK.create(),
                        "insertion", Algorithm.INSERTION.create());
                long sortersDone = Math.max(r.left.finishedAtNanos(), r.right.finishedAtNanos());
                if (r.merger.startedAtNanos() >= sortersDone) ok++;
            }
            return new Outcome(ok + "/100", ok == 100);
        });
        check("Sorter бүр зөвхөн өөрийн мужид бичнэ", "trace, n = 200, 25 хослол", "25/25 зөрчилгүй", () -> {
            int ok = 0;
            int[] in = DataGenerator.generate(200, 9, 1, 100, DataGenerator.Pattern.RANDOM);
            for (Algorithm l : Algorithm.values()) {
                for (Algorithm r : Algorithm.values()) {
                    TraceRecorder rec = new TraceRecorder(System.nanoTime());
                    SortResult res = MultithreadedSorter.sort(in.clone(), l.key, l.create(), r.key, r.create(), rec);
                    if (rangesRespected(rec.snapshot(), res.mid, in.length)) ok++;
                }
            }
            return new Outcome(ok + "/25 зөрчилгүй", ok == 25);
        });
    }

    // =====================================================================
    // 7. Event trace
    // =====================================================================
    private static void traceTests() {
        category("Event trace");
        for (Algorithm a : Algorithm.values()) {
            check("Trace replay = бодит үр дүн, " + a.key, "int-limits.txt", "replay ижил, seq тасралтгүй", () -> {
                int[] in = InputParser.parseFile(root.resolve("examples/int-limits.txt").toString());
                TraceRecorder rec = new TraceRecorder(System.nanoTime());
                SortResult r = MultithreadedSorter.sort(in, a.key, a.create(), "merge", Algorithm.MERGE.create(), rec);
                List<TraceEvent> ev = rec.snapshot();
                int[] result = replayResult(in, ev);
                boolean seqOk = true;
                for (int i = 0; i < ev.size(); i++) if (ev.get(i).seq != i) seqOk = false;
                boolean ok = Arrays.equals(result, r.result) && seqOk && !rec.truncated();
                return new Outcome(ok ? "replay ижил, seq тасралтгүй" : "replay зөрүүтэй", ok);
            });
        }
        check("JSON формат", "номын жишээ", "\"format\":\"ii-4-6-trace/1\", events байна", () -> {
            int[] in = InputParser.parseList(BOOK);
            TraceRecorder rec = new TraceRecorder(System.nanoTime());
            SortResult r = MultithreadedSorter.sort(in, "merge", Algorithm.MERGE.create(), "quick", Algorithm.QUICK.create(), rec);
            String json = TraceJson.toJson(r, rec, r.totalNanos);
            boolean ok = json.contains("\"format\":\"ii-4-6-trace/1\"") && json.contains("\"events\"")
                    && json.contains("\"M_DONE\"");
            return new Outcome(ok ? "\"format\":\"ii-4-6-trace/1\", events байна" : "формат буруу", ok);
        });
        check("Parent event-ийн дараалал", "номын жишээ", "RUN_START → START ×2 → JOIN ×2 → START → JOIN → RUN_END", () -> {
            TraceRecorder rec = new TraceRecorder(System.nanoTime());
            MultithreadedSorter.sort(InputParser.parseList(BOOK), "merge", Algorithm.MERGE.create(),
                    "merge", Algorithm.MERGE.create(), rec);
            StringBuilder sb = new StringBuilder();
            for (TraceEvent e : rec.snapshot()) {
                if (e.thread.equals(MultithreadedSorter.PARENT_NAME)) sb.append(e.type).append(' ');
            }
            String s = sb.toString().trim();
            boolean ok = s.equals("RUN_START START START JOIN JOIN START JOIN RUN_END");
            return new Outcome(ok ? "RUN_START → START ×2 → JOIN ×2 → START → JOIN → RUN_END" : s, ok);
        });
    }

    // =====================================================================
    // 8. Lockstep (Semaphore)
    // =====================================================================
    private static void lockstepTests() {
        category("Lockstep");
        check("Permit 0 үед sorter-ууд WAITING", "номын жишээ", "Sorter-L WAITING, Sorter-R WAITING, 0 үйлдэл", () -> {
            Live live = Live.start(InputParser.parseList(BOOK), Algorithm.INSERTION, Algorithm.INSERTION);
            try {
                live.awaitWaiting(2);
                Thread l = live.thread(MultithreadedSorter.LEFT_NAME);
                Thread r = live.thread(MultithreadedSorter.RIGHT_NAME);
                String actual = "Sorter-L " + l.getState() + ", Sorter-R " + r.getState() + ", " + live.gate.consumed() + " үйлдэл";
                return new Outcome(actual, actual.equals("Sorter-L WAITING, Sorter-R WAITING, 0 үйлдэл"));
            } finally {
                live.cancel();
            }
        });
        check("1 permit = яг 1 үйлдэл", "номын жишээ, 20 алхам", "20 алхам → 20 үйлдэл", () -> {
            Live live = Live.start(InputParser.parseList(BOOK), Algorithm.INSERTION, Algorithm.BUBBLE);
            try {
                live.awaitWaiting(2);
                int bad = 0;
                for (int i = 1; i <= 20; i++) {
                    live.gate.release(1);
                    live.awaitConsumed(i);
                    live.awaitWaiting(1);
                    if (live.gate.consumed() != i) bad++;
                }
                String actual = "20 алхам → " + live.gate.consumed() + " үйлдэл";
                return new Outcome(actual, bad == 0 && live.gate.consumed() == 20);
            } finally {
                live.cancel();
            }
        });
        check("Fair semaphore: L, R ээлжилнэ", "номын жишээ, 10 алхам", "L R L R ... эсвэл R L R L ... (ээлжилсэн)", () -> {
            Live live = Live.start(InputParser.parseList(BOOK), Algorithm.INSERTION, Algorithm.INSERTION);
            try {
                live.awaitWaiting(2);
                for (int i = 1; i <= 10; i++) {
                    live.gate.release(1);
                    live.awaitConsumed(i);
                    live.awaitWaiting(2);
                }
                StringBuilder sb = new StringBuilder();
                for (TraceEvent e : live.rec.snapshot()) {
                    if (e.type.startsWith("CMP") || e.type.equals("SWP") || e.type.equals("SET")) {
                        sb.append(e.thread.equals(MultithreadedSorter.LEFT_NAME) ? "L " : "R ");
                    }
                }
                // which sorter reaches the gate first is up to the OS scheduler;
                // after that the fair (FIFO) semaphore makes them strictly alternate
                String s = sb.toString().trim();
                boolean ok = s.equals("L R L R L R L R L R") || s.equals("R L R L R L R L R L");
                return new Outcome(s, ok);
            } finally {
                live.cancel();
            }
        });
        check("Хангалттай permit өгөхөд дуусна", "n = 60, bubble + quick", "зөв эрэмбэлэгдэж, TERMINATED ×3", () -> {
            int[] in = DataGenerator.generate(60, 4, -50, 50, DataGenerator.Pattern.RANDOM);
            Live live = Live.start(in, Algorithm.BUBBLE, Algorithm.QUICK);
            live.gate.release(1_000_000);
            live.parent.join(10_000);
            boolean terminated = live.threads.size() == 3
                    && live.threads.stream().allMatch(t -> t.getState() == Thread.State.TERMINATED);
            boolean ok = live.result != null && isSortedCopyOf(in, live.result.result) && terminated;
            return new Outcome(ok ? "зөв эрэмбэлэгдэж, TERMINATED ×3" : "дуусаагүй эсвэл буруу", ok);
        });
        check("Цуцлахад thread үлдэхгүй", "n = 60, 5 алхамын дараа цуцлах", "бүх thread TERMINATED", () -> {
            int[] in = DataGenerator.generate(60, 4, -50, 50, DataGenerator.Pattern.RANDOM);
            Live live = Live.start(in, Algorithm.SELECTION, Algorithm.MERGE);
            live.awaitWaiting(2);
            live.gate.release(5);
            live.awaitConsumed(5);
            live.cancel();
            live.parent.join(5_000);
            for (Thread t : live.threads) t.join(5_000);
            boolean ok = !live.parent.isAlive() && live.threads.stream().noneMatch(Thread::isAlive);
            return new Outcome(ok ? "бүх thread TERMINATED" : "амьд thread үлдсэн", ok);
        });
    }

    /** A lockstep run driven from the test, like LiveSession does for the web UI. */
    private static final class Live {
        final StepGate gate = new StepGate();
        final TraceRecorder rec = new TraceRecorder(System.nanoTime(), TraceRecorder.DEFAULT_MAX_EVENTS, true);
        final List<Thread> threads = new CopyOnWriteArrayList<>();
        Thread parent;
        volatile SortResult result;

        static Live start(int[] input, Algorithm l, Algorithm r) {
            Live live = new Live();
            RunOptions opt = new RunOptions(live.rec, live.gate, live.threads::add);
            live.parent = new Thread(() -> {
                try {
                    live.result = MultithreadedSorter.sort(input, l.key, l.create(), r.key, r.create(), opt);
                } catch (Exception e) {
                    // cancelled or interrupted: result stays null
                }
            }, "Parent-test");
            live.parent.start();
            return live;
        }

        Thread thread(String name) {
            for (Thread t : threads) if (t.getName().equals(name)) return t;
            throw new IllegalStateException("thread not found: " + name);
        }

        void awaitWaiting(int count) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5_000;
            while (gate.waitingThreads() < count) {
                if (System.currentTimeMillis() > deadline) throw new IllegalStateException("threads did not reach the gate");
                Thread.sleep(2);
            }
            // queue length is counted before the thread is parked; wait until it really is
            while (System.currentTimeMillis() <= deadline) {
                long waiting = threads.stream().filter(t -> t.getState() == Thread.State.WAITING).count();
                if (waiting >= count) return;
                Thread.sleep(2);
            }
        }

        void awaitConsumed(long n) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5_000;
            while (gate.consumed() < n) {
                if (System.currentTimeMillis() > deadline) throw new IllegalStateException("step not consumed");
                Thread.sleep(1);
            }
        }

        void cancel() {
            gate.cancel();
        }
    }

    // =====================================================================
    // 9. Command line (separate JVM, checks output and exit code)
    // =====================================================================
    private static void cliTests() {
        category("Командын мөр");
        cli("Номын жишээ файлаас", new String[] {"--file", "examples/book-example.txt"}, 0, "[2, 3, 4, 6, 7, 8, 12, 15, 18, 19]");
        cli("Сондгой n = 11", new String[] {"--file", "examples/odd-11.txt", "--algo", "insertion"}, 0, "зүүн [0,6) хэмжээ 6, баруун [6,11) хэмжээ 5");
        cli("Давхардалтай файл", new String[] {"--file", "examples/duplicates.txt", "--algo-left", "bubble", "--algo-right", "quick"}, 0, "[1, 1, 1, 2, 2, 3, 3, 3, 3, 5, 5, 5]");
        cli("Урвуу файл", new String[] {"--file", "examples/reversed.txt", "--algo", "selection"}, 0, "[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]");
        cli("int-ийн хязгаар файл", new String[] {"--file", "examples/int-limits.txt"}, 0, "[-2147483648, -100, -1, 0, 0, 7, 100, 2147483647]");
        cli("Ганц элемент", new String[] {"--file", "examples/single.txt"}, 0, "[42]");
        cli("Санамсаргүй өгөгдөл", new String[] {"--random", "500", "--seed", "1", "--algo", "quick"}, 0, "ЗӨВ");
        cli("Оролтгүй", new String[] {}, 2, "Алдаа");
        cli("Тоо биш утгатай файл", new String[] {"--file", "examples/invalid-letters.txt"}, 2, "Алдаа");
        cli("Хоосон файл", new String[] {"--file", "examples/empty.txt"}, 2, "Алдаа");
        cli("Буруу алгоритм", new String[] {"--input", "1,2", "--algo", "foo"}, 2, "foo");
        cli("--help", new String[] {"--help"}, 0, "--algo");
    }

    private static void cli(String name, String[] args, int expectedExit, String expectedText) {
        String shown = args.length == 0 ? "(аргументгүй)" : String.join(" ", args);
        check(name, shown, "exit " + expectedExit + ", \"" + expectedText + "\"", () -> {
            List<String> cmd = new ArrayList<>();
            cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
            cmd.add("-cp");
            cmd.add(root.resolve("out").toString());
            cmd.add("sorting.Main");
            cmd.addAll(Arrays.asList(args));
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(root.toFile()).redirectErrorStream(true);
            pb.environment().remove("JAVA_TOOL_OPTIONS");
            Process p = pb.start();
            String out = readAll(p.getInputStream());
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Outcome("хугацаа хэтэрсэн", false);
            }
            int exit = p.exitValue();
            boolean found = out.contains(expectedText);
            String actual = "exit " + exit + ", " + (found ? "\"" + expectedText + "\"" : "текст олдсонгүй");
            return new Outcome(actual, exit == expectedExit && found);
        });
    }

    // =====================================================================
    // helpers
    // =====================================================================
    private static void category(String name) {
        currentCategory = name;
        System.out.println();
        System.out.println("== " + name + " ==");
    }

    private static void check(String name, String input, String expected, Body body) {
        long t = System.nanoTime();
        Outcome o;
        try {
            o = body.run();
        } catch (Throwable e) {
            o = new Outcome("алдаа: " + e, false);
        }
        long ms = (System.nanoTime() - t) / 1_000_000;
        Row row = new Row(ROWS.size() + 1, currentCategory, name, input, expected, o.actual, o.pass, ms);
        ROWS.add(row);
        System.out.printf("  [%s] %3d. %s%s%n", o.pass ? "OK  " : "FAIL", row.no, name,
                o.pass ? "" : "  (хүлээгдсэн: " + expected + "; бодит: " + o.actual + ")");
    }

    private static void sortCase(String name, String input, String expected) {
        check(name, input, expected, () -> {
            int[] in = InputParser.parseList(input);
            int ok = 0;
            String first = null;
            for (Algorithm l : Algorithm.values()) {
                for (Algorithm r : Algorithm.values()) {
                    SortResult res = MultithreadedSorter.sort(in.clone(), l.key, l.create(), r.key, r.create());
                    String s = join(res.result);
                    if (first == null) first = s;
                    if (s.equals(expected)) ok++;
                }
            }
            // all 25 left/right algorithm pairs must give the expected list
            return new Outcome(ok == 25 ? first : ok + "/25 хослол зөв, жишээ: " + first, ok == 25);
        });
    }

    private static Outcome sortedOutcome(int[] in, Algorithm l, Algorithm r) throws InterruptedException {
        SortResult res = MultithreadedSorter.sort(in, l.key, l.create(), r.key, r.create());
        String s = join(res.result);
        return new Outcome(s, isSortedCopyOf(in, res.result) && s.equals(BOOK_SORTED));
    }

    private static void parseError(String name, String input, String mustMention) {
        check(name, input.isBlank() ? "\"" + input + "\"" : input, "InputException (\"" + mustMention + "\" дурдана)", () -> {
            try {
                int[] a = InputParser.parseList(input);
                return new Outcome("алдаа гараагүй: " + join(a), false);
            } catch (InputException e) {
                boolean ok = e.getMessage().toLowerCase().contains(mustMention.toLowerCase());
                return new Outcome(ok ? "InputException (\"" + mustMention + "\" дурдана)" : e.getMessage(), ok);
            }
        });
    }

    private static void limitError(String name, int n, Algorithm l, Algorithm r, boolean force, boolean trace, String mustMention) {
        check(name, "n=" + n + ", " + l.key + "+" + r.key + (trace ? ", trace" : ""), "InputException (" + mustMention + ")", () -> {
            try {
                Limits.check(n, l, r, force, trace);
                return new Outcome("алдаа гараагүй", false);
            } catch (InputException e) {
                boolean ok = e.getMessage().replace(" ", "").contains(mustMention);
                return new Outcome(ok ? "InputException (" + mustMention + ")" : e.getMessage(), ok);
            }
        });
    }

    private static void generatorError(String name, long n, long min, long max, String mustMention) {
        check(name, "n=" + n + ", min=" + min + ", max=" + max, "InputException", () -> {
            try {
                DataGenerator.generate(n, 1, min, max, DataGenerator.Pattern.RANDOM);
                return new Outcome("алдаа гараагүй", false);
            } catch (InputException e) {
                boolean ok = e.getMessage().contains(mustMention);
                return new Outcome(ok ? "InputException" : e.getMessage(), ok);
            }
        });
    }

    private static void fileError(String name, String file) {
        check(name, file, "InputException", () -> {
            try {
                int[] a = InputParser.parseFile(root.resolve(file).toString());
                return new Outcome("алдаа гараагүй: " + join(a), false);
            } catch (InputException e) {
                return new Outcome("InputException", true);
            }
        });
    }

    /** Re-applies the recorded sorter operations to a copy of the input. */
    private static int[] replayList(int[] input, List<TraceEvent> events) {
        int[] list = input.clone();
        for (TraceEvent e : events) {
            if (e.type.equals("SWP")) {
                int t = list[(int) e.a];
                list[(int) e.a] = list[(int) e.b];
                list[(int) e.b] = t;
            } else if (e.type.equals("SET")) {
                list[(int) e.a] = (int) e.b;
            }
        }
        return list;
    }

    /** Re-applies all recorded operations and returns the rebuilt result[] array. */
    private static int[] replayResult(int[] input, List<TraceEvent> events) {
        int[] list = input.clone();
        int[] result = new int[input.length];
        for (TraceEvent e : events) {
            switch (e.type) {
                case "SWP": {
                    int t = list[(int) e.a];
                    list[(int) e.a] = list[(int) e.b];
                    list[(int) e.b] = t;
                    break;
                }
                case "SET":
                    list[(int) e.a] = (int) e.b;
                    break;
                case "MW":
                    if (list[(int) e.b] != (int) e.c) throw new IllegalStateException("MW value mismatch at seq " + e.seq);
                    result[(int) e.a] = list[(int) e.b];
                    break;
                default:
                    break;
            }
        }
        return result;
    }

    /** Sorter-L may only touch [0, mid), Sorter-R only [mid, n). */
    private static boolean rangesRespected(List<TraceEvent> events, int mid, int n) {
        for (TraceEvent e : events) {
            boolean left = e.thread.equals(MultithreadedSorter.LEFT_NAME);
            boolean right = e.thread.equals(MultithreadedSorter.RIGHT_NAME);
            if (!left && !right) continue;
            int lo = left ? 0 : mid;
            int hi = left ? mid : n;
            switch (e.type) {
                case "CMP": case "SWP":
                    if (e.a < lo || e.a >= hi || e.b < lo || e.b >= hi) return false;
                    break;
                case "CMPV": case "SET":
                    if (e.a < lo || e.a >= hi) return false;
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    private static boolean isSortedCopyOf(int[] input, int[] result) {
        int[] expected = input.clone();
        Arrays.sort(expected);
        return Arrays.equals(expected, result);
    }

    private static String join(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(a[i]);
        }
        return sb.toString();
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        in.transferTo(buf);
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static String cell(String s) {
        String t = s.replace("|", "\\|").replace("\n", " ");
        return t.length() > 70 ? t.substring(0, 67) + "..." : t;
    }

    private static Path writeMarkdown(long passed) throws IOException {
        Path dir = root.resolve("tests").resolve("results");
        Files.createDirectories(dir);
        Path file = dir.resolve("test-results.md");
        StringBuilder sb = new StringBuilder();
        sb.append("# Тестийн үр дүн\n\n");
        sb.append("Энэ файлыг `./test.sh` (Windows: `test.bat`) автоматаар үүсгэсэн. Хүлээгдсэн утгыг тестийн кодонд")
          .append(" урьдчилан бичсэн бөгөөд бодит утга нь програмыг ажиллуулж гарсан үр дүн юм.\n\n");
        sb.append("| | |\n|---|---|\n");
        sb.append("| Огноо | ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append(" |\n");
        sb.append("| Java | ").append(System.getProperty("java.vendor")).append(' ')
          .append(System.getProperty("java.runtime.version")).append(" |\n");
        sb.append("| OS | ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
          .append(" (").append(System.getProperty("os.arch")).append(") |\n");
        sb.append("| CPU цөм | ").append(Runtime.getRuntime().availableProcessors()).append(" |\n");
        sb.append("| Нийт | ").append(ROWS.size()).append(" тест, ").append(passed).append(" амжилттай, ")
          .append(ROWS.size() - passed).append(" алдаатай |\n\n");

        String cat = null;
        for (Row r : ROWS) {
            if (!r.category.equals(cat)) {
                cat = r.category;
                long n = ROWS.stream().filter(x -> x.category.equals(r.category)).count();
                long ok = ROWS.stream().filter(x -> x.category.equals(r.category) && x.pass).count();
                sb.append("\n## ").append(cat).append(" (").append(ok).append('/').append(n).append(")\n\n");
                sb.append("| № | Тест | Оролт | Хүлээгдсэн | Бодит | Үр дүн |\n");
                sb.append("|---:|---|---|---|---|:---:|\n");
            }
            sb.append("| ").append(r.no).append(" | ").append(cell(r.name)).append(" | `").append(cell(r.input))
              .append("` | ").append(cell(r.expected)).append(" | ").append(cell(r.actual)).append(" | ")
              .append(r.pass ? "✅" : "❌").append(" |\n");
        }
        sb.append("\nТэмдэглэл: \"Том өгөгдөл\" хэсгийн хугацаа нь машинаас хамаарна; зөвхөн зөв эсэхийг шалгана.\n");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private TestRunner() { }
}
