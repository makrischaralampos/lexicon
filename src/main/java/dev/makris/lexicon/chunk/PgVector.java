package dev.makris.lexicon.chunk;

/** Helpers for pgvector's text representation, e.g. "[0.1,0.2,0.3]". */
public final class PgVector {

    private PgVector() {}

    public static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
