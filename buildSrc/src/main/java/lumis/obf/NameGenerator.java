package lumis.obf;

import java.util.Random;

/**
 * Deterministic, seed-driven generator of short unique identifiers.
 * Same seed -> same stream, so builds are reproducible.
 */
public final class NameGenerator {

    private final char[] first;
    private final char[] rest;
    private long counter;

    public NameGenerator(long seed) {
        // Identifier-safe characters. Bytecode does not care about Java keywords.
        String firstChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String restChars = firstChars + "0123456789";
        this.first = shuffle(firstChars.toCharArray(), new Random(seed));
        this.rest = shuffle(restChars.toCharArray(), new Random(seed ^ 0x9E3779B97F4A7C15L));
        this.counter = 0;
    }

    /** Next unique identifier, e.g. "a", "Qz", "m3x". */
    public String next() {
        long n = counter++;
        StringBuilder sb = new StringBuilder();
        sb.append(first[(int) (n % first.length)]);
        n /= first.length;
        while (n > 0) {
            n -= 1;
            sb.append(rest[(int) (n % rest.length)]);
            n /= rest.length;
        }
        return sb.toString();
    }

    private static char[] shuffle(char[] arr, Random r) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            char tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
        return arr;
    }
}
