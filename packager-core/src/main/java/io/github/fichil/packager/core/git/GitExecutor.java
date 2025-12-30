package io.github.fichil.packager.core.git;

import io.github.fichil.packager.core.exec.ProcessExecutor;

import java.io.File;

public class GitExecutor {

    private final ProcessExecutor pe;

    public GitExecutor(ProcessExecutor pe) {
        this.pe = pe;
    }

    public void cloneRepo(String gitUrl, File repoDir, boolean shallow) throws Exception {
        // 保持你原逻辑：clone 到目标目录
        if (shallow) {
            runGit(repoDir.getParentFile(), "clone", "--depth", "1", gitUrl, repoDir.getAbsolutePath());
        } else {
            runGit(repoDir.getParentFile(), "clone", gitUrl, repoDir.getAbsolutePath());
        }
    }

    public void checkoutAndPull(File repoDir, String branch) throws Exception {

        // 0) fix origin fetch refspec to fetch ALL branches (avoid single-branch clones)
        try {
            runGit(repoDir, "config", "--unset-all", "remote.origin.fetch");
        } catch (RuntimeException ignore) {
            // ignore if not exists
        }
        runGit(repoDir, "config", "--add", "remote.origin.fetch", "+refs/heads/*:refs/remotes/origin/*");

        // 1) fetch
        runGit(repoDir, "fetch", "--prune", "origin");

        // 2) checkout local; if missing, create from origin/<branch>
        try {
            runGit(repoDir, "checkout", branch);
        } catch (RuntimeException ex) {
            runGit(repoDir, "checkout", "-B", branch, "origin/" + branch);
        }

        // 3) pull
        runGit(repoDir, "pull", "--ff-only", "origin", branch);
    }


    private boolean remoteBranchExists(File repoDir, String remote, String branch) throws Exception {
        // git show-ref --verify --quiet refs/remotes/<remote>/<branch>
        try {
            runGit(repoDir, "show-ref", "--verify", "--quiet", "refs/remotes/" + remote + "/" + branch);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String detectRemoteName(File repoDir) throws Exception {
        // 先尝试 origin 是否存在：git remote get-url origin
        try {
            runGit(repoDir, "remote", "get-url", "origin");
            return "origin";
        } catch (RuntimeException ex) {
            // origin 不存在 -> 取第一个 remote 名称
        }

        // 没有工具读取 stdout，所以用“尝试常见远端名”的方式兜底
        // 你内网 git 一般是 origin；这里再多试一个 upstream
        try {
            runGit(repoDir, "remote", "get-url", "upstream");
            return "upstream";
        } catch (RuntimeException ex2) {
            // still unknown
        }

        // 实在无法确定，就返回 null（上层会默认 origin）
        return null;
    }

    private void runGit(File dir, String... args) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<String>();
        cmd.add("git");
        for (int i = 0; i < args.length; i++) {
            cmd.add(args[i]);
        }
        pe.run(cmd, dir);
    }

}
