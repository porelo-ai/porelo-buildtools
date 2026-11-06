package org.fuin.porelo.release;

import java.util.Comparator;

/**
 * Orders package versions as dpkg does, so {@code 0.5.0~snapshot.290} sorts before {@code
 * 0.5.1~snapshot.292} and {@code ~} before anything, the end of a version included.
 *
 * <p>The snapshots' RPM versions are the same strings, and rpm orders them alike.
 */
public final class DebianVersion {

    /** Lower first. */
    public static final Comparator<String> ORDER = DebianVersion::compare;

    private DebianVersion() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Compares two versions of the form {@code [epoch:]upstream[-revision]}.
     *
     * @param left one version
     * @param right the other
     * @return below zero when left is older, zero when equal, above zero when newer
     */
    public static int compare(String left, String right) {
        final int epoch = Long.compare(epoch(left), epoch(right));
        if (epoch != 0) {
            return epoch;
        }
        final String l = withoutEpoch(left);
        final String r = withoutEpoch(right);
        final int upstream = part(upstream(l), upstream(r));
        return upstream != 0 ? upstream : part(revision(l), revision(r));
    }

    private static long epoch(String version) {
        final int colon = version.indexOf(':');
        return colon < 0 ? 0 : Long.parseLong(version.substring(0, colon));
    }

    private static String withoutEpoch(String version) {
        final int colon = version.indexOf(':');
        return colon < 0 ? version : version.substring(colon + 1);
    }

    private static String upstream(String version) {
        final int dash = version.lastIndexOf('-');
        return dash < 0 ? version : version.substring(0, dash);
    }

    private static String revision(String version) {
        final int dash = version.lastIndexOf('-');
        return dash < 0 ? "" : version.substring(dash + 1);
    }

    /** dpkg's verrevcmp: runs of non-digits by their characters' weight, then runs of digits. */
    private static int part(String left, String right) {
        int i = 0;
        int j = 0;
        while (i < left.length() || j < right.length()) {
            while ((i < left.length() && !isDigit(left.charAt(i)))
                    || (j < right.length() && !isDigit(right.charAt(j)))) {
                final int l = weight(left, i);
                final int r = weight(right, j);
                if (l != r) {
                    return l - r;
                }
                i++;
                j++;
            }
            while (i < left.length() && left.charAt(i) == '0') {
                i++;
            }
            while (j < right.length() && right.charAt(j) == '0') {
                j++;
            }
            int first = 0;
            while (i < left.length()
                    && isDigit(left.charAt(i))
                    && j < right.length()
                    && isDigit(right.charAt(j))) {
                if (first == 0) {
                    first = left.charAt(i) - right.charAt(j);
                }
                i++;
                j++;
            }
            if (i < left.length() && isDigit(left.charAt(i))) {
                return 1;
            }
            if (j < right.length() && isDigit(right.charAt(j))) {
                return -1;
            }
            if (first != 0) {
                return first;
            }
        }
        return 0;
    }

    /** A character's weight in a non-digit run; past the end and a digit weigh nothing. */
    private static int weight(String text, int at) {
        if (at >= text.length()) {
            return 0;
        }
        final char c = text.charAt(at);
        if (isDigit(c)) {
            return 0;
        }
        if (((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
            return c;
        }
        if (c == '~') {
            return -1;
        }
        return c + 256;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
