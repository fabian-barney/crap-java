package media.barney.crap4java.gradle;

import media.barney.crap4java.core.Main;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

public abstract class Crap4JavaCheckTask extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getAnalysisRoot();

    @TaskAction
    void runCheck() throws Exception {
        int exit = Main.runWithExistingCoverage(
                new String[0],
                getAnalysisRoot().get().getAsFile().toPath(),
                System.out,
                System.err
        );
        if (exit != 0) {
            throw new GradleException("crap4javaCheck failed with exit " + exit);
        }
    }
}
