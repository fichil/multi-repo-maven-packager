package io.github.fichil.packager.core.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class ProcessExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProcessExecutor.class);

    public void run(List<String> command, File workDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.inheritIO(); // 直接输出到控制台，便于排错

        log.info("Exec: {} (dir={})", String.join(" ", command), workDir.getAbsolutePath());

        Process p = pb.start();
        int code = p.waitFor();
        if (code != 0) {
            throw new RuntimeException("Command failed with exit code " + code + ": " + String.join(" ", command));
        }
    }
}
