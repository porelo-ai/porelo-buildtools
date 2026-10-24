package org.fuin.porelo.release;

import gg.jte.Content;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** The immutable data exposed to the compiled release-page templates. */
public record PlanTemplate(String release, String goal, String stand, List<Sprint> sprints) {
    /** Explains how the plan's issue states and operator tests are related. */
    public static final String INTRODUCTION =
            "Each sprint ends in functions the operator tests by hand; a bar shows the states of a sprint's issues, and a mark per function shows whether it is tested.";

    /** A state group in the reference page's color order. */
    public record State(String color, String label) {}

    /**
     * A state's colour as the light palette writes it, for an SVG shown as an image, where no
     * stylesheet of the page and no CSS variable reaches its attributes; the image's own style
     * switches it for a dark scheme.
     *
     * @param color a state's colour name, as {@link #STATES} names it
     * @return the colour as {@code #rrggbb}
     */
    public static String hex(String color) {
        return switch (color) {
            case "done" -> "#0ca30c";
            case "review" -> "#2a78d6";
            case "impl" -> "#fab219";
            case "backlog" -> "#8ac8f0";
            case "estimated" -> "#9575b5";
            case "analyzed" -> "#77766f";
            case "accepted" -> "#b9b8b2";
            case "idea" -> "#eb6834";
            case "new" -> "#ffffff";
            default -> throw new IllegalArgumentException("no colour named " + color);
        };
    }

    /** The legend also fixes the order of the segments in every sprint bar. */
    public static final List<State> STATES =
            List.of(
                    new State("done", "Finished / Rejected"),
                    new State("review", "Review"),
                    new State("impl", "Implementing / Rework"),
                    new State("backlog", "Backlog"),
                    new State("estimated", "Estimated"),
                    new State("analyzed", "Analyzed"),
                    new State("accepted", "Accepted"),
                    new State("idea", "Idea"),
                    new State("new", "Not filed yet / Missing"));

    /** An issue link or a note that has not been filed. */
    public record Chip(String text, String detail, String color, String link) {
        /**
         * Fixed Markdown squares: green, blue, yellow, a square button for light blue, purple,
         * black, white, orange, and outlined white.
         */
        public String emoji() {
            return switch (color) {
                case "done" -> "🟩";
                case "review" -> "🟦";
                case "impl" -> "🟨";
                case "backlog" -> "🔲";
                case "estimated" -> "🟪";
                case "analyzed" -> "⬛";
                case "accepted" -> "⬜";
                case "idea" -> "🟧";
                case "new" -> "◻️";
                default -> throw new IllegalArgumentException("Unknown chip color: " + color);
            };
        }
    }

    /** A function's instructions and the person's recorded test. */
    public record Function(String title, String test, String tested, List<Chip> chips) {}

    /** One counted state with its position in a 400-unit bar. */
    public record Segment(String color, String label, int count, double x, double width) {}

    /** A sprint and the already escaped SVG generated from its segments. */
    public record Sprint(
            String id,
            String title,
            String goal,
            List<Function> functions,
            List<Segment> segments,
            int done,
            int total,
            String svg,
            String counts) {
        /** Only template-generated SVG is inserted as markup. */
        public Content image() {
            return output -> output.writeContent(svg);
        }
    }

    static PlanTemplate from(Plan plan, Map<String, IssueIndex> issues, String stand) {
        final List<Sprint> sprints = new ArrayList<>();
        for (final Plan.Sprint sprint : plan.sprints()) {
            final List<Function> functions = new ArrayList<>();
            final Map<String, Integer> counts = new LinkedHashMap<>();
            final var counted = new LinkedHashSet<String>();
            final Map<String, Integer> states = new LinkedHashMap<>();
            int done = 0;
            for (final Plan.Function function : sprint.functions()) {
                final List<Chip> chips = new ArrayList<>();
                for (final String named : function.issues()) {
                    final Chip chip = chip(named, issues.get(named));
                    chips.add(chip);
                    if (counted.add(named)) {
                        counts.merge(chip.color(), 1, Integer::sum);
                        final IssueIndex row = issues.get(named);
                        states.merge(row == null ? "Missing" : row.state(), 1, Integer::sum);
                        if (chip.color().equals("done")) done++;
                    }
                }
                for (final String note : function.notes()) {
                    chips.add(new Chip(note, "Not filed yet", "new", ""));
                    counts.merge("new", 1, Integer::sum);
                    states.merge("Not filed yet", 1, Integer::sum);
                }
                functions.add(
                        new Function(
                                function.title(),
                                function.test(),
                                function.tested() == null ? "" : function.tested(),
                                List.copyOf(chips)));
            }
            final int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            final List<Segment> segments = new ArrayList<>();
            int offset = 0;
            for (final State state : STATES) {
                final int count = counts.getOrDefault(state.color(), 0);
                if (count > 0) {
                    segments.add(
                            new Segment(
                                    state.color(),
                                    state.label(),
                                    count,
                                    400.0 * offset / total,
                                    400.0 * count / total));
                    offset += count;
                }
            }
            final String stateCounts =
                    STATES.stream()
                            .flatMap(state -> java.util.Arrays.stream(state.label().split(" / ")))
                            .filter(states::containsKey)
                            .map(state -> state + " " + states.get(state))
                            .collect(java.util.stream.Collectors.joining(", "));
            final Sprint data =
                    new Sprint(
                            sprint.id(),
                            sprint.title(),
                            sprint.goal(),
                            List.copyOf(functions),
                            List.copyOf(segments),
                            done,
                            total,
                            "",
                            stateCounts);
            sprints.add(
                    new Sprint(
                            data.id(),
                            data.title(),
                            data.goal(),
                            data.functions(),
                            data.segments(),
                            done,
                            total,
                            PlanRender.template("sprint.jte", data),
                            stateCounts));
        }
        return new PlanTemplate(plan.release(), plan.goal(), stand, List.copyOf(sprints));
    }

    private static Chip chip(String named, @org.jspecify.annotations.Nullable IssueIndex row) {
        if (row == null) return new Chip(named, "Missing index row", "new", "");
        final String color =
                switch (row.state()) {
                    case "Finished", "Rejected" -> "done";
                    case "Review" -> "review";
                    case "Implementing", "Rework" -> "impl";
                    case "Backlog" -> "backlog";
                    case "Estimated" -> "estimated";
                    case "Analyzed" -> "analyzed";
                    case "Accepted" -> "accepted";
                    case "Idea" -> "idea";
                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown issue state: " + row.state());
                };
        return new Chip(
                row.number(),
                named + " · " + row.state() + " · " + row.size() + " · " + row.title(),
                color,
                row.link(named.split("#")[0]));
    }
}
