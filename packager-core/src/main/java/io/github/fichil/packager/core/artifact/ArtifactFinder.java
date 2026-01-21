package io.github.fichil.packager.core.artifact;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ArtifactFinder {

    public static List<File> findWars(File repoDir) {
        List<File> result = new ArrayList<>();
        scan(repoDir, result);
        return result;
    }

    private static void scan(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                if ("target".equals(f.getName())) {
                    collectWar(f, result);
                } else {
                    scan(f, result);
                }
            }
        }
    }

    private static void collectWar(File targetDir, List<File> result) {
        File[] wars = targetDir.listFiles((d, name) ->
                name.endsWith(".war")
                        && !name.startsWith("original-")
                        && !name.endsWith("-sources.war")
        );
        if (wars != null) {
            for (File war : wars) {
                result.add(war);
            }
        }
    }
}

