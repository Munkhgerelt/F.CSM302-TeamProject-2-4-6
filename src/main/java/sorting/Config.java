package sorting;

import sorting.algo.Algorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed command-line options.
 * Each sorting thread may use its own algorithm (--algo-left / --algo-right);
 * --algo sets both at once.
 */
final class Config {
    String inputText;               // raw integer list text
    Algorithm left = Algorithm.MERGE;
    Algorithm right = Algorithm.MERGE;
    boolean help;
    boolean listAlgos;

    static Config parse(String[] args) {
        Config c = new Config();
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h":
                case "--help":
                    c.help = true;
                    break;
                case "--list-algos":
                    c.listAlgos = true;
                    break;
                case "--input":
                    c.inputText = value(args, ++i, a);
                    break;
                case "--algo": {
                    Algorithm al = algorithm(value(args, ++i, a));
                    c.left = al;
                    c.right = al;
                    break;
                }
                case "--algo-left":
                    c.left = algorithm(value(args, ++i, a));
                    break;
                case "--algo-right":
                    c.right = algorithm(value(args, ++i, a));
                    break;
                default:
                    if (a.startsWith("--")) {
                        throw new IllegalArgumentException("үл мэдэгдэх сонголт '" + a + "'. Тусламж: --help");
                    }
                    positional.add(a);
            }
        }
        if (c.inputText == null && !positional.isEmpty()) {
            c.inputText = String.join(" ", positional);
        } else if (c.inputText != null && !positional.isEmpty()) {
            throw new IllegalArgumentException("--input болон байршлын тоонуудыг зэрэг өгч болохгүй.");
        }
        return c;
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) {
            throw new IllegalArgumentException(option + " сонголтод утга өгөөгүй байна.");
        }
        return args[i];
    }

    private static Algorithm algorithm(String key) {
        Algorithm a = Algorithm.fromKey(key);
        if (a == null) {
            throw new IllegalArgumentException("'" + key + "' гэсэн алгоритм байхгүй. Боломжит: " + Algorithm.keysList());
        }
        return a;
    }
}
