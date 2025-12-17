package io.github.fichil.packager.core.git;

import io.github.fichil.packager.core.exec.ProcessExecutor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GitExecutor {

    private final ProcessExecutor exec;

    public GitExecutor(ProcessExecutor exec) {
        this.exec = exec;
    }

    public void checkoutAndPull(File repoDir, String branch) throws Exception {
        ensureDir(repoDir);

        runGit(repoDir, "fetch", "--all", "--prune");
        runGit(repoDir, "checkout", branch);
        runGit(repoDir, "pull", "--ff-only");
    }

    private void runGit(File repoDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<String>();
        cmd.add("git");
        cmd.addAll(java.util.Arrays.asList(args));
        exec.run(cmd, repoDir);
    }

    private void ensureDir(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Repo path not found or not a directory: " + (dir == null ? "null" : dir.getAbsolutePath()));
        }
    }
}
