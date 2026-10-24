package sorting;

import sorting.algo.Algorithm;
import sorting.input.DataGenerator;
import sorting.input.InputException;
import sorting.input.InputParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed command-line options.
 * Exactly one input source must be given: --input / positional numbers,
 * --file, or --random. Each sorting thread may use its own algorithm.
 */
final class Config {
    // input sources
    String inputText;
    String file;
    Long randomN;
    // generator options
    Long seed;
    long min = DataGenerator.DEFAULT_MIN;
    long max = DataGenerator.DEFAULT_MAX;
    DataGenerator.Pattern pattern = DataGenerator.Pattern.RANDOM;
    // algorithms
    Algorithm left = Algorithm.MERGE;
    Algorithm right = Algorithm.MERGE;
    // flags
    String tracePath;    // --trace FILE: write JSON event trace
    boolean web;         // --web: start the local animation server
    int port = 8080;
    boolean force;
    boolean full;
    boolean help;
    boolean listAlgos;

    /** Human-readable description of where the data came from (filled by {@link #resolveInput()}). */
    String sourceDescription;

    static Config parse(String[] args) {
        Config c = new Config();
        List<String> positional = new ArrayList<>();
        boolean generatorOptionUsed = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h":
                case "--help": c.help = true; break;
                case "--list-algos": c.listAlgos = true; break;
                case "--force": c.force = true; break;
                case "--full": c.full = true; break;
                case "--trace": c.tracePath = value(args, ++i, a); break;
                case "--web": c.web = true; break;
                case "--port": {
                    long p = InputParser.parseOptionLong(a, value(args, ++i, a));
                    if (p < 1 || p > 65535) throw new InputException("--port 1..65535 мужид байх ёстой (одоо " + p + ").");
                    c.port = (int) p;
                    break;
                }
                case "--input": c.inputText = value(args, ++i, a); break;
                case "--file": c.file = value(args, ++i, a); break;
                case "--random": c.randomN = InputParser.parseOptionLong(a, value(args, ++i, a)); break;
                case "--seed": c.seed = InputParser.parseOptionLong(a, value(args, ++i, a)); generatorOptionUsed = true; break;
                case "--min": c.min = InputParser.parseOptionLong(a, value(args, ++i, a)); generatorOptionUsed = true; break;
                case "--max": c.max = InputParser.parseOptionLong(a, value(args, ++i, a)); generatorOptionUsed = true; break;
                case "--pattern": c.pattern = DataGenerator.Pattern.fromKey(value(args, ++i, a)); generatorOptionUsed = true; break;
                case "--algo": {
                    Algorithm al = algorithm(value(args, ++i, a));
                    c.left = al;
                    c.right = al;
                    break;
                }
                case "--algo-left": c.left = algorithm(value(args, ++i, a)); break;
                case "--algo-right": c.right = algorithm(value(args, ++i, a)); break;
                default:
                    if (a.startsWith("--")) {
                        throw new InputException("Үл мэдэгдэх сонголт '" + a + "'. Тусламж: --help");
                    }
                    positional.add(a);
            }
        }
        if (!positional.isEmpty()) {
            if (c.inputText != null) {
                throw new InputException("--input болон байршлын тоонуудыг зэрэг өгч болохгүй.");
            }
            c.inputText = String.join(" ", positional);
        }
        if (c.help || c.listAlgos) return c;
        if (c.web) {
            if (c.inputText != null || c.file != null || c.randomN != null) {
                throw new InputException("--web горимд өгөгдлийг хөтөч дээрээс оруулна; --input/--file/--random хэрэггүй.");
            }
            return c;
        }

        int sources = (c.inputText != null ? 1 : 0) + (c.file != null ? 1 : 0) + (c.randomN != null ? 1 : 0);
        if (sources == 0) {
            throw new InputException("Оролтын өгөгдөл өгөөгүй байна. --input \"7,12,19\", --file examples/book-example.txt"
                    + " эсвэл --random 20 --seed 1-ийн аль нэгийг ашиглана уу.");
        }
        if (sources > 1) {
            throw new InputException("Оролтын эх үүсвэрийг зөвхөн нэгийг сонгоно (--input / --file / --random).");
        }
        if (generatorOptionUsed && c.randomN == null) {
            throw new InputException("--seed, --min, --max, --pattern сонголтууд зөвхөн --random-тай хамт ажиллана.");
        }
        return c;
    }

    /** Reads / generates the integer list according to the chosen source. */
    int[] resolveInput() {
        if (inputText != null) {
            sourceDescription = "гараас оруулсан";
            return InputParser.parseList(inputText);
        }
        if (file != null) {
            sourceDescription = "файл: " + file;
            return InputParser.parseFile(file);
        }
        long s = (seed != null) ? seed : System.currentTimeMillis();
        sourceDescription = "random n=" + randomN + " seed=" + s + " муж=[" + min + "," + max + "] хэлбэр=" + pattern.key;
        return DataGenerator.generate(randomN, s, min, max, pattern);
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length || args[i].startsWith("--")) {
            throw new InputException(option + " сонголтод утга өгөөгүй байна.");
        }
        return args[i];
    }

    private static Algorithm algorithm(String key) {
        Algorithm a = Algorithm.fromKey(key);
        if (a == null) {
            throw new InputException("'" + key + "' гэсэн алгоритм байхгүй. Боломжит: " + Algorithm.keysList());
        }
        return a;
    }
}
