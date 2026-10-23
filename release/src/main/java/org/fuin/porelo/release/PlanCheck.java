package org.fuin.porelo.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Refuses inconsistent progress claims and a page that no longer reflects its inputs. */
final class PlanCheck {
    private PlanCheck() {}

    static void check(
            Path file,
            Plan plan,
            Map<String, IssueIndex> issues,
            PlanRender.Pages pages,
            IssueLinks links)
            throws IOException, Stop {
        final List<String> faults = new ArrayList<>();
        final Map<String, String> assigned = new HashMap<>();
        for (final Plan.Sprint sprint : plan.sprints()) {
            for (final Plan.Function function : sprint.functions()) {
                final String where = "sprint " + sprint.id() + " function " + function.title();
                if (function.issues().isEmpty()
                        && function.notes().isEmpty()
                        && function.tested() == null) {
                    faults.add(where + ": no issue, no note and no tested date");
                }
                final java.util.Set<String> within = new java.util.HashSet<>();
                for (final String named : function.issues()) {
                    if (!within.add(named)) {
                        faults.add(where + ": duplicate issue " + named + " in function");
                        continue;
                    }
                    final String previous = assigned.putIfAbsent(named, where);
                    if (previous != null) {
                        faults.add(named + ": assigned twice: " + previous + " and " + where);
                    }
                    final IssueIndex row = issues.get(named);
                    if (row == null) {
                        faults.add(
                                where
                                        + ": "
                                        + named
                                        + " has no row in its index "
                                        + links.issueIndexSource(named));
                    } else if (row.state().equals("Finished") && function.tested() == null) {
                        faults.add(where + ": " + named + " is Finished without tested");
                    }
                }
            }
        }
        final Map<Path, String> expected = new java.util.LinkedHashMap<>();
        expected.put(PlanRender.page(file, plan), pages.markdown());
        pages.images()
                .forEach(
                        (name, svg) ->
                                expected.put(file.toAbsolutePath().resolveSibling(name), svg));
        for (final var entry : expected.entrySet()) {
            final Path page = entry.getKey();
            if (!Files.isRegularFile(page)) {
                faults.add(page + ": page missing; run plan render " + file);
            } else if (!Files.readString(page).equals(entry.getValue())) {
                faults.add(page + ": page differs from plan render " + file);
            }
        }
        if (!faults.isEmpty()) {
            throw Stop.refused(String.join("\n", faults));
        }
    }
}
