package sorting.trace;

/** Tiny JSON helpers (the project deliberately has no external dependencies). */
public final class Json {
    private Json() { }

    public static String str(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
            }
        }
        return sb.append('"').toString();
    }

    public static void intArray(StringBuilder sb, int[] a) {
        sb.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(a[i]);
        }
        sb.append(']');
    }

    /** Appends one event as a compact JSON object; unused fields are omitted. */
    public static void event(StringBuilder sb, TraceEvent e) {
        sb.append("{\"seq\":").append(e.seq)
          .append(",\"t\":").append(e.t)
          .append(",\"th\":").append(str(e.thread))
          .append(",\"type\":").append(str(e.type));
        if (e.a != TraceEvent.NONE) sb.append(",\"a\":").append(e.a);
        if (e.b != TraceEvent.NONE) sb.append(",\"b\":").append(e.b);
        if (e.c != TraceEvent.NONE) sb.append(",\"c\":").append(e.c);
        if (e.note != null) sb.append(",\"note\":").append(str(e.note));
        sb.append('}');
    }
}
