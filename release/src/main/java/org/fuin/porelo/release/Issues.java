package org.fuin.porelo.release;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import org.fuin.porelo.stories.IssuesIndex;

/**
 * A repository's {@code issues/}: each issue's data in the JSON head right below its heading, and
 * each directory's index generated from the heads.
 */
final class Issues {

    private final PrintStream out;
    private final PrintStream err;

    Issues(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Writes each directory's index from its files' heads.
     *
     * @param dir the issues directory
     * @return 0, {@link Stop#REFUSED} with each fault said, or {@link Stop#UNANSWERED} when
     *     something cannot be read or written
     */
    int index(Path dir) {
        try {
            final IssuesIndex.Result result = IssuesIndex.write(dir);
            if (report(dir, result, "fault(s) in the issues in ")) return Stop.REFUSED;
            out.println(
                    "OK    "
                            + result.written().size()
                            + " index(es) under "
                            + dir
                            + " written from the issues' heads");
            return 0;
        } catch (IOException ex) {
            err.println("could not read or write " + dir + ": " + Web.reason(ex));
            return Stop.UNANSWERED;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return Stop.REFUSED;
        }
    }

    /**
     * Checks every head, what it names, and each index against what the heads give.
     *
     * @param dir the issues directory
     * @return 0, {@link Stop#REFUSED} with each fault said, or {@link Stop#UNANSWERED} when it
     *     cannot be read
     */
    int check(Path dir) {
        try {
            final IssuesIndex.Result result = IssuesIndex.check(dir);
            if (report(dir, result, "fault(s) in the issues in ")) return Stop.REFUSED;
            out.println(
                    "OK    issue heads in "
                            + dir
                            + ", each by issue-1.json, each wait and parent an issue, each index as"
                            + " they give it");
            return 0;
        } catch (IOException ex) {
            err.println("could not read " + dir + ": " + Web.reason(ex));
            return Stop.UNANSWERED;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return Stop.REFUSED;
        }
    }

    /**
     * Gives each issue file without a head one from its prose lines and its index row where they
     * agree; lists every difference where they do not.
     *
     * @param dir the issues directory
     * @return 0 when no difference was found, {@link Stop#REFUSED} with each difference said, or
     *     {@link Stop#UNANSWERED} when something cannot be read or written
     */
    int convert(Path dir) {
        try {
            final IssuesIndex.Result result = IssuesIndex.convert(dir);
            result.notes().forEach(out::println);
            if (report(dir, result, "difference(s), nothing written for those files, in ")) {
                return Stop.REFUSED;
            }
            out.println(
                    "OK    "
                            + result.written().size()
                            + " issue file(s) under "
                            + dir
                            + " given a head; run porelo-release issues index "
                            + dir);
            return 0;
        } catch (IOException ex) {
            err.println("could not read or write " + dir + ": " + Web.reason(ex));
            return Stop.UNANSWERED;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return Stop.REFUSED;
        }
    }

    private boolean report(Path dir, IssuesIndex.Result result, String what) {
        if (result.faults().isEmpty()) return false;
        result.faults().forEach(fault -> err.println(dir + "/" + fault));
        err.println(result.faults().size() + " " + what + dir);
        return true;
    }
}
