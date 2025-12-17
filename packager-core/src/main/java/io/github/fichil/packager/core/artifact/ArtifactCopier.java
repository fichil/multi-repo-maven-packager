package io.github.fichil.packager.core.artifact;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ArtifactCopier {

    public void copy(File from, File to) throws Exception {
        if (!from.exists() || !from.isFile()) {
            throw new IllegalArgumentException("Artifact not found: " + from.getAbsolutePath());
        }
        Path targetDir = to.getParentFile().toPath();
        Files.createDirectories(targetDir);

        Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
