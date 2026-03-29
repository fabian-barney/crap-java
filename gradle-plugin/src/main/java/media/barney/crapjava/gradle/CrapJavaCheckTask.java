package media.barney.crapjava.gradle;

import media.barney.crapjava.core.Main;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.util.Comparator;
import java.util.List;

public abstract class CrapJavaCheckTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getAnalysisRoot();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAnalysisSources();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getCoverageReports();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAnalysisMetadata();

    @TaskAction
    void runCheck() throws Exception {
        List<String> arguments = getAnalysisSources().getFiles().stream()
                .map(file -> getAnalysisRoot().get().getAsFile().toPath().relativize(file.toPath()).toString())
                .sorted(Comparator.naturalOrder())
                .toList();
        if (arguments.isEmpty()) {
            getLogger().lifecycle("No Java files to analyze.");
            return;
        }
        try (var out = GradleLoggingPrintStreams.standardOut(getLogger());
             var err = GradleLoggingPrintStreams.standardErr(getLogger())) {
            int exit = Main.runWithExistingCoverage(
                    arguments.toArray(String[]::new),
                    getAnalysisRoot().get().getAsFile().toPath(),
                    out,
                    err
            );
            if (exit != 0) {
                throw new GradleException("crap-java-check failed with exit " + exit);
            }
        }
    }
}
