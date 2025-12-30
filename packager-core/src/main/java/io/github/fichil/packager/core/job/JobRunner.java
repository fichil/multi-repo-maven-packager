package io.github.fichil.packager.core.job;

import io.github.fichil.packager.core.artifact.ArtifactCopier;
import io.github.fichil.packager.core.config.PackagerConfig;
import io.github.fichil.packager.core.config.VarResolver;
import io.github.fichil.packager.core.git.GitExecutor;
import io.github.fichil.packager.core.maven.MavenExecutor;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobRunner {

    private final GitExecutor git;
    private final MavenExecutor mvn;
    private final ArtifactCopier copier;
    private final Map<String, String> vars;
    private final boolean dryRun; // 新增

    public JobRunner(GitExecutor git,
                     MavenExecutor mvn,
                     ArtifactCopier copier,
                     Map<String, String> vars,
                     boolean dryRun) {
        this.git = git;
        this.mvn = mvn;
        this.copier = copier;
        this.vars = vars;
        this.dryRun = dryRun;
    }

    public void runJob(PackagerConfig.JobConfig job, boolean skipTests) throws Exception {
        if (job == null) throw new IllegalArgumentException("Job is null");

        Map<String, File> repoNameToDir = new HashMap<String, File>();

        // 1) repos
        List<PackagerConfig.RepoConfig> repos = job.getRepos();
        if (repos != null) {
            for (int i = 0; i < repos.size(); i++) {
                PackagerConfig.RepoConfig r = repos.get(i);

                String name = r.getName();
                String repoPath = VarResolver.resolve(r.getPath(), vars);
                String branch = VarResolver.resolve(r.getBranch(), vars);
                String gitUrl = VarResolver.resolve(r.getGitUrl(), vars);

                String shallowStr = VarResolver.resolve(r.getShallow(), vars);
                boolean shallow = "true".equalsIgnoreCase(shallowStr); // 或 Boolean.parseBoolean

                if (repoPath == null || repoPath.trim().isEmpty()) {
                    throw new IllegalArgumentException("Repo path is empty (repo=" + name + ")");
                }
                if (branch == null || branch.trim().isEmpty()) {
                    throw new IllegalArgumentException("Repo branch is empty (repo=" + name + ")");
                }

                File repoDir = new File(repoPath);

                // 1.1) 不存在则 clone
                if (!repoDir.exists()) {
                    if (gitUrl == null || gitUrl.trim().isEmpty()) {
                        throw new IllegalStateException("Repo not exist and gitUrl not set: " + repoPath + " (repo=" + name + ")");
                    }
                    File parent = repoDir.getParentFile();
                    if (parent != null && !parent.exists() && !dryRun) parent.mkdirs();

                    System.out.println("[PLAN] git clone" + (shallow ? " --depth 1 " : " ") + gitUrl + " -> " + repoDir.getAbsolutePath());

                    if (!dryRun) {
                        git.cloneRepo(gitUrl, repoDir, shallow);
                    } else {
                        // dry-run: 假定 clone 成功，允许后续 plan 继续输出
                        repoNameToDir.put(name, repoDir);
                        // 并且不要继续做真实校验/checkout/mvn
                        continue;
                    }
                }



                if (!repoDir.isDirectory()) {
                    throw new IllegalArgumentException("Repo path is not a directory: " + repoPath + " (repo=" + name + ")");
                }

                repoNameToDir.put(name, repoDir);

                // 1.2) checkout & pull
                System.out.println("[PLAN] git checkout/pull " + branch + " (" + name + ")");
                git.checkoutAndPull(repoDir, branch);

                // 1.3) maven
                if (r.getMaven() != null && r.getMaven().getGoals() != null && !r.getMaven().getGoals().isEmpty()) {
                    String workDir = r.getMaven().getWorkDir() != null ? r.getMaven().getWorkDir() : ".";
                    workDir = VarResolver.resolve(workDir, vars);

                    File mvnDir = ".".equals(workDir) ? repoDir : new File(repoDir, workDir);
                    List<String> goals = r.getMaven().getGoals();

                    System.out.println("[PLAN] mvn " + goals + " (dir=" + mvnDir.getAbsolutePath() + ")");
                    mvn.runGoals(mvnDir, goals, skipTests);
                }
            }
        }

        // 2) artifacts
        PackagerConfig.ArtifactsConfig artifacts = job.getArtifacts();
        if (artifacts != null && artifacts.getFiles() != null && !artifacts.getFiles().isEmpty()) {
            String outDirStr = VarResolver.resolve(artifacts.getOutputDir(), vars);
            if (outDirStr == null || outDirStr.trim().isEmpty()) {
                throw new IllegalArgumentException("artifacts.outputDir is empty");
            }
            File outDir = new File(outDirStr);

            for (int i = 0; i < artifacts.getFiles().size(); i++) {
                PackagerConfig.ArtifactFile f = artifacts.getFiles().get(i);

                File repoDir = repoNameToDir.get(f.getRepo());
                if (repoDir == null) {
                    throw new IllegalArgumentException("Artifact repo not found in job repos: " + f.getRepo());
                }

                String fromStr = VarResolver.resolve(f.getFrom(), vars);
                String toStr = VarResolver.resolve(f.getTo(), vars);

                File from = new File(repoDir, fromStr);
                File to = new File(outDir, toStr);

                System.out.println("[PLAN] copy " + from.getAbsolutePath() + " -> " + to.getAbsolutePath());
                copier.copy(from, to);
            }
        }
    }
}
