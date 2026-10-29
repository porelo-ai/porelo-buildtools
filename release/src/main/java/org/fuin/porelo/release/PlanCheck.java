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

    /** From Review on, a change is pushed and published as a snapshot another one can build on. */
    private static final Set<String> PUBLISHED = Set.of("Review", "Finished");

    /** An issue no release plans is no wait once it is published or will never be built. */
    private static final Set<String> DONE = Set.of("Review", "Finished", "Rejected");

    private PlanCheck() {}

    static void check(
            Path file,
            Plan plan,
            Map<String, IssueIndex> issues,
            PlanRender.Pages pages,
            IssueLinks links,
            Plan.Releases releases)
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
        checkAfter(plan, issues, releases, links, faults);
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

    /**
     * A wait holds when both issues have rows, no issue waits on itself through others, work starts
     * only once what it waits for is published, and nothing waits for a later sprint or release. A
     * number in an index's Blocked by cell is a wait inside that repository, as an entry in {@value
     * Plan#AFTER} is, and waits for an issue no release plans only once that one is done.
     */
    private static void checkAfter(
            Plan plan,
            Map<String, IssueIndex> issues,
            Plan.Releases releases,
            IssueLinks links,
            List<String> faults) {
        final Map<String, Plan.Placement> placements = releases.placed();
        final Set<String> mine = new java.util.LinkedHashSet<>();
        final Set<String> worked = new java.util.HashSet<>();
        for (final Plan.Sprint sprint : plan.sprints()) {
            for (final Plan.Function function : sprint.functions()) {
                mine.addAll(Plan.placedIn(function));
                worked.addAll(function.issues());
            }
        }
        // A parent named only under covers is Implementing while its parts run, so only work
        // waits to start.
        final Set<String> parents = new java.util.HashSet<>(mine);
        parents.removeAll(worked);
        final Set<String> missing = new java.util.LinkedHashSet<>();
        final Map<String, List<String>> blocked = new java.util.LinkedHashMap<>();
        for (final String issue : mine) {
            final IssueIndex row = issues.get(issue);
            if (row == null) {
                continue;
            }
            final String repository = issue.split("#")[0];
            for (final String number : row.blockedBy()) {
                final String target = repository + "#" + number;
                blocked.computeIfAbsent(issue, key -> new ArrayList<>()).add(target);
                final String where = issue + " Blocked by";
                final IssueIndex waited = issues.get(target);
                if (waited == null) {
                    if (missing.add(target)) {
                        faults.add(
                                where
                                        + ": "
                                        + target
                                        + " has no row in its index "
                                        + links.issueIndexSource(target));
                    }
                    continue;
                }
                if (!parents.contains(issue)) {
                    checkStarted(where, issue, row, target, waited, faults);
                }
                checkCovered(where, issue, target, releases, faults);
                if (!placements.containsKey(target) && !DONE.contains(waited.state())) {
                    faults.add(
                            where
                                    + ": "
                                    + issue
                                    + " waits for "
                                    + target
                                    + ", planned in no release, while it is "
                                    + waited.state());
                }
            }
        }
        for (final var wait : plan.after().entrySet()) {
            final String issue = wait.getKey();
            final String where = Plan.AFTER + " " + issue;
            final IssueIndex row = issues.get(issue);
            if (row == null && missing.add(issue)) {
                faults.add(
                        where
                                + ": "
                                + issue
                                + " has no row in its index "
                                + links.issueIndexSource(issue));
            }
            for (final String target : wait.getValue()) {
                checkCovered(where, issue, target, releases, faults);
                final IssueIndex waited = issues.get(target);
                if (waited == null) {
                    if (missing.add(target)) {
                        faults.add(
                                where
                                        + ": "
                                        + target
                                        + " has no row in its index "
                                        + links.issueIndexSource(target));
                    }
                    continue;
                }
                if (row != null && !parents.contains(issue)) {
                    checkStarted(where, issue, row, target, waited, faults);
                }
            }
        }
        final Map<String, List<String>> waits = new java.util.LinkedHashMap<>();
        plan.after().forEach((issue, targets) -> waits.put(issue, new ArrayList<>(targets)));
        blocked.forEach(
                (issue, targets) -> {
                    final List<String> all = waits.computeIfAbsent(issue, key -> new ArrayList<>());
                    targets.stream().filter(target -> !all.contains(target)).forEach(all::add);
                });
        for (final String issue : waits.keySet()) {
            final Plan.Placement at = placements.get(issue);
            if (at == null || !mine.contains(issue)) {
                continue;
            }
            // An issue outside this plan's sprints still carries the wait on to the next one.
            reached(issue, waits)
                    .forEach(
                            (target, through) -> {
                                final Plan.Placement theirs = placements.get(target);
                                if (theirs != null && theirs.isAfter(at)) {
                                    faults.add(
                                            (plan.after()
                                                                    .getOrDefault(issue, List.of())
                                                                    .contains(through)
                                                            ? Plan.AFTER + " " + issue
                                                            : issue + " Blocked by")
                                                    + ": "
                                                    + issue
                                                    + " in sprint "
                                                    + at.sprint()
                                                    + " waits for "
                                                    + target
                                                    + " in the later sprint "
                                                    + theirs.sprint()
                                                    + (theirs.release().equals(at.release())
                                                            ? ""
                                                            : " of release " + theirs.release())
                                                    + (through.equals(target)
                                                            ? ""
                                                            : " through " + through));
                                }
                            });
        }
        final List<String> cycle = cycle(waits);
        if (!cycle.isEmpty()) {
            faults.add(Plan.AFTER + ": a cycle of waits: " + String.join(" after ", cycle));
        }
    }

    /** A covered requirement is the acceptance of the whole; a wait names the part it needs. */
    private static void checkCovered(
            String where,
            String issue,
            String target,
            Plan.Releases releases,
            List<String> faults) {
        if (releases.covered().contains(target)) {
            faults.add(
                    where
                            + ": "
                            + issue
                            + " waits for "
                            + target
                            + ", a requirement a release's plan has covered; name the part it waits for instead");
        }
    }

    /** Work starts only once what it waits for is published. */
    private static void checkStarted(
            String where,
            String issue,
            IssueIndex row,
            String target,
            IssueIndex waited,
            List<String> faults) {
        if (STARTED.contains(row.state()) && !PUBLISHED.contains(waited.state())) {
            faults.add(
                    where
                            + ": "
                            + issue
                            + " is "
                            + row.state()
                            + " while "
                            + target
                            + ", which it waits for, is "
                            + waited.state());
        }
    }

    /** Returns every issue an issue waits for, directly or not, with the wait it is reached by. */
    private static Map<String, String> reached(String issue, Map<String, List<String>> after) {
        final Map<String, String> reached = new java.util.LinkedHashMap<>();
        final java.util.Deque<String> todo = new java.util.ArrayDeque<>();
        for (final String target : after.getOrDefault(issue, List.of())) {
            if (reached.putIfAbsent(target, target) == null) {
                todo.add(target);
            }
        }
        while (!todo.isEmpty()) {
            final String next = todo.poll();
            for (final String target : after.getOrDefault(next, List.of())) {
                if (!target.equals(issue)
                        && reached.putIfAbsent(target, reached.get(next)) == null) {
                    todo.add(target);
                }
            }
        }
        return reached;
    }

    /** Returns one cycle among the waits, its first issue repeated at the end; empty if none. */
    private static List<String> cycle(Map<String, List<String>> after) {
        final Set<String> done = new java.util.HashSet<>();
        for (final String start : after.keySet()) {
            final List<String> path = new ArrayList<>();
            final List<String> found = walk(start, after, path, done);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return List.of();
    }

    private static List<String> walk(
            String issue, Map<String, List<String>> after, List<String> path, Set<String> done) {
        final int at = path.indexOf(issue);
        if (at >= 0) {
            final List<String> cycle = new ArrayList<>(path.subList(at, path.size()));
            cycle.add(issue);
            return cycle;
        }
        if (!done.add(issue)) {
            return List.of();
        }
        path.add(issue);
        for (final String target : after.getOrDefault(issue, List.of())) {
            final List<String> found = walk(target, after, path, done);
            if (!found.isEmpty()) {
                return found;
            }
        }
        path.removeLast();
        return List.of();
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
