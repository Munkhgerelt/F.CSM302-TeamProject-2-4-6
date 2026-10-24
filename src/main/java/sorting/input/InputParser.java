package sorting.input;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

/**
 * Parses and validates user-supplied integer lists.
 * Accepted separators: comma, semicolon, any whitespace (incl. new lines).
 * In files, everything after '#' on a line is a comment.
 * All errors are reported as {@link InputException} with a user-facing
 * (Mongolian) message that names the offending token and its position.
 */
public final class InputParser {

    private InputParser() { }

    public static int[] parseList(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new InputException("Оролт хоосон байна. Дор хаяж нэг бүхэл тоо оруулна уу, жишээ нь: 7,12,19,3");
        }
        String[] tokens = text.trim().split("[,;\\s]+");
        int count = 0;
        for (String t : tokens) if (!t.isEmpty()) count++;
        if (count == 0) {
            throw new InputException("Оролтод тоо алга (зөвхөн тусгаарлагч тэмдэгт байна).");
        }
        if (count > Limits.HARD_MAX_N) {
            throw new InputException("Хэт олон утга: " + count + ". Дээд хязгаар n ≤ " + Limits.HARD_MAX_N + ".");
        }
        int[] values = new int[count];
        int k = 0;
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            values[k] = parseToken(t, k + 1);
            k++;
        }
        return values;
    }

    /** Parses one token; position is 1-based for the error message. */
    static int parseToken(String token, int position) {
        String t = token.trim();
        if (!t.matches("[+-]?\\d+")) {
            if (t.matches("[+-]?\\d*[.,]\\d+([eE][+-]?\\d+)?") || t.matches("[+-]?\\d+[eE][+-]?\\d+")) {
                throw new InputException(position + "-р утга '" + shorten(t) + "' нь бутархай тоо. Зөвхөн бүхэл тоо оруулна.");
            }
            throw new InputException(position + "-р утга '" + shorten(t) + "' нь бүхэл тоо биш.");
        }
        try {
            return Integer.parseInt(t.startsWith("+") ? t.substring(1) : t);
        } catch (NumberFormatException e) {
            throw new InputException(position + "-р утга '" + shorten(t) + "' нь int мужаас хэтэрсэн. Зөвшөөрөгдөх муж: ["
                    + Integer.MIN_VALUE + ", " + Integer.MAX_VALUE + "].");
        }
    }

    /** Reads integers from a text file; '#' starts a comment. */
    public static int[] parseFile(String path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new InputException("Файл олдсонгүй: " + path);
        } catch (IOException e) {
            throw new InputException("Файлыг уншиж чадсангүй: " + path + " (" + e.getMessage() + ")");
        } catch (RuntimeException e) {
            throw new InputException("Файлын замыг ашиглах боломжгүй: " + path + " (" + e.getMessage() + ")");
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            int hash = line.indexOf('#');
            sb.append(hash >= 0 ? line.substring(0, hash) : line).append('\n');
        }
        if (sb.toString().trim().isEmpty()) {
            throw new InputException("Файл хоосон байна (тайлбараас өөр тоо алга): " + path);
        }
        return parseList(sb.toString());
    }

    /** Parses an integer option value such as --random 100. */
    public static long parseOptionLong(String option, String value) {
        String v = value.trim();
        if (!v.matches("[+-]?\\d+")) {
            throw new InputException(option + " сонголтын утга бүхэл тоо байх ёстой: '" + shorten(v) + "'");
        }
        try {
            return Long.parseLong(v.startsWith("+") ? v.substring(1) : v);
        } catch (NumberFormatException e) {
            throw new InputException(option + " сонголтын утга хэт том: '" + shorten(v) + "'");
        }
    }

    private static String shorten(String s) {
        return s.length() <= 24 ? s : s.substring(0, 21) + "...";
    }
}
