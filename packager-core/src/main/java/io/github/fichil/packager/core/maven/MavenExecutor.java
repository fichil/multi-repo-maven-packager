package io.github.fichil.packager.core.maven;

import io.github.fichil.packager.core.exec.ProcessExecutor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenExecutor {

    private final ProcessExecutor exec;
    private final String mavenExecutable;

    public MavenExecutor(ProcessExecutor exec, String mavenExecutable) {
        this.exec = exec;
        this.mavenExecutable = (mavenExecutable == null || mavenExecutable.trim().isEmpty())
                ? "mvn"
                : mavenExecutable;
    }

    public void runGoals(File workDir, List<String> goals, boolean skipTests) throws Exception {
        if (goals == null || goals.isEmpty()) {
            return;
        }

        List<String> cmd = new ArrayList<String>();
        cmd.add(this.mavenExecutable);
        cmd.addAll(goals);
        if (skipTests) {
            cmd.add("-DskipTests");
        }
        exec.run(cmd, workDir);
    }
}

