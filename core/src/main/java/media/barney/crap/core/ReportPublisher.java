package media.barney.crap.core;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ReportPublisher {

    private ReportPublisher() {
    }

    static void publish(CrapReport report, ReportOptions options, PrintStream out) throws IOException {
        publishPrimary(report, options, out);
        Path junitReportPath = options.junitReportPath();
        if (junitReportPath != null) {
            write(junitReportPath, ReportFormatter.format(report, ReportFormat.JUNIT));
        }
    }

    private static void publishPrimary(CrapReport report, ReportOptions options, PrintStream out) throws IOException {
        String content = ReportFormatter.format(
                report,
                options.format(),
                options.failuresOnly(),
                options.omitRedundancy(),
                options.includeExclusionAudit()
        );
        Path outputPath = options.outputPath();
        if (outputPath == null) {
            out.print(content);
            return;
        }
        write(outputPath, content);
    }

    private static void write(Path path, String content) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
