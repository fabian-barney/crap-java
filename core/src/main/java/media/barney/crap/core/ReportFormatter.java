package media.barney.crap.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

final class ReportFormatter {

    private ReportFormatter() {
    }

    static String format(List<MethodMetrics> entries) {
        List<MethodMetrics> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator
                .comparing((MethodMetrics e) -> e.crapScore() == null)
                .thenComparing(e -> e.crapScore() == null ? 0.0 : -e.crapScore()));

        String header = String.format("%-30s %-35s %4s %7s %8s", "Method", "Class", "CC", "Cov%", "CRAP");
        String separator = "-".repeat(header.length());
        StringBuilder builder = new StringBuilder();
        builder.append("CRAP Report\n");
        builder.append("===========\n");
        builder.append(header).append('\n');
        builder.append(separator).append('\n');

        for (MethodMetrics entry : sorted) {
            builder.append(String.format(Locale.ROOT, "%-30s %-35s %4d %7s %8s",
                    entry.methodName(),
                    entry.className(),
                    entry.complexity(),
                    formatCoverage(entry.coveragePercent()),
                    formatCrap(entry.crapScore())));
            builder.append('\n');
        }

        return builder.toString();
    }

    private static String formatCoverage(@Nullable Double coverage) {
        if (coverage == null) {
            return "  N/A ";
        }
        return String.format(Locale.ROOT, "%5.1f%%", coverage);
    }

    private static String formatCrap(@Nullable Double score) {
        if (score == null) {
            return "     N/A";
        }
        return String.format(Locale.ROOT, "%8.1f", score);
    }
}

