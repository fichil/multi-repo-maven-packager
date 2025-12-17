package io.github.fichil.packager.core.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class ProcessExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProcessExecutor.class);

    private boolean dryRun = false;

    public void run(List<String> command, File workDir) throws Exception {
        String cmdLine = String.join(" ", command);
        String dir = (workDir == null ? "null" : workDir.getAbsolutePath());

        if (dryRun) {
            System.out.println("[DRY-RUN] " + cmdLine + " (dir=" + dir + ")");
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.inheritIO();

        log.info("Exec: {} (dir={})", cmdLine, dir);

        Process p = pb.start();
        int code = p.waitFor();
        if (code != 0) {
            throw new RuntimeException("Command failed with exit code " + code + ": " + cmdLine);
        }
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
