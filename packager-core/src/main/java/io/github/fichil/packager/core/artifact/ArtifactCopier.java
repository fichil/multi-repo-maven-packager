package io.github.fichil.packager.core.artifact;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ArtifactCopier {

    private final boolean dryRun;

    public ArtifactCopier() {
        this(false);
    }

    public ArtifactCopier(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public void copy(File from, File to) throws Exception {
        if (dryRun) {
            // 只打印，不落盘
            System.out.println("[DRY-RUN] skip copy: " + from.getAbsolutePath() + " -> " + to.getAbsolutePath());
            return;
        }

        if (!from.exists() || !from.isFile()) {
            throw new IllegalArgumentException("Artifact not found: " + from.getAbsolutePath());
        }
        Path targetDir = to.getParentFile().toPath();
        Files.createDirectories(targetDir);

        Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

}
