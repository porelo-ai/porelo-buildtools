package org.fuin.porelo.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Refuses inconsistent progress claims and a page that no longer reflects its inputs. */
final class PlanCheck {
    private static final Set<String> BEFORE_IMPLEMENTING =
            Set.of("Idea", "Accepted", "Analyzed", "Estimated", "Backlog");
    private static final Set<String> STARTED =
            Set.of("Implementing", "Rework", "Review", "Finished");

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
        checkCovers(plan, issues, assigned, links, faults);
        final Map<Path, String> expected = new java.util.LinkedHashMap<>();
        expected.put(PlanRender.page(file), pages.markdown());
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

    private static void checkCovers(
            Plan plan,
            Map<String, IssueIndex> issues,
            Map<String, String> assigned,
            IssueLinks links,
            List<String> faults) {
        for (final Plan.Sprint sprint : plan.sprints()) {
            for (final Plan.Function function : sprint.functions()) {
                final String where = "sprint " + sprint.id() + " function " + function.title();
                final List<String> started = new ArrayList<>();
                for (final String named : function.issues()) {
                    final IssueIndex row = issues.get(named);
                    if (row != null && STARTED.contains(row.state())) {
                        started.add(named + " is " + row.state());
                    }
                }
                final Set<String> within = new java.util.HashSet<>();
                for (final String named : function.covers()) {
                    if (!within.add(named)) {
                        faults.add(where + ": duplicate covered parent " + named + " in function");
                        continue;
                    }
                    final String assignment = assigned.get(named);
                    if (assignment != null) {
                        faults.add(
                                where
                                        + ": covered parent "
                                        + named
                                        + " is also listed under issues in "
                                        + assignment);
                    }
                    final IssueIndex row = issues.get(named);
                    if (row == null) {
                        faults.add(
                                where
                                        + ": "
                                        + named
                                        + " has no row in its index "
                                        + links.issueIndexSource(named));
                    } else if (BEFORE_IMPLEMENTING.contains(row.state()) && !started.isEmpty()) {
                        faults.add(
                                where
                                        + ": covered parent "
                                        + named
                                        + " is "
                                        + row.state()
                                        + " before Implementing while "
                                        + String.join(", ", started));
                    }
                }
            }
        }
    }
}
