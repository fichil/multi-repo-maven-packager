package io.github.fichil.packager.core.job;

import io.github.fichil.packager.core.artifact.ArtifactCopier;
import io.github.fichil.packager.core.artifact.ArtifactFinder;
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
    private final ArtifactFinder artifactFinder;

    public JobRunner(GitExecutor git,
                     MavenExecutor mvn,
                     ArtifactFinder artifactFinder,
                     ArtifactCopier copier,
                     Map<String, String> vars,
                     boolean dryRun) {
        this.git = git;
        this.mvn = mvn;
        this.artifactFinder = artifactFinder;
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

                if (from.exists() && from.isFile()) {
                    // 1) 按配置精确复制（保持现有行为）
                    System.out.println("[PLAN] copy " + from.getAbsolutePath() + " -> " + to.getAbsolutePath());
                    copier.copy(from, to);
                } else {
                    // 2) fallback：配置 from 不存在时，自动发现 war
                    System.out.println("[WARN] Artifact not found by config: " + from.getAbsolutePath());
                    System.out.println("[PLAN] fallback to auto-discover war under repo: " + repoDir.getAbsolutePath());

                    List<File> wars = artifactFinder.findWars(repoDir);
                    if (wars == null || wars.isEmpty()) {
                        throw new IllegalArgumentException("No war found under repo: " + repoDir.getAbsolutePath()
                                + " (repo=" + f.getRepo() + ", from=" + fromStr + ")");
                    }

                    // 如果 toStr 指定了文件名，则复制到这个文件名；否则复制所有 war 到 output 根
                    boolean toLooksLikeFile = toStr != null && toStr.toLowerCase().endsWith(".war");

                    if (toLooksLikeFile) {
                        // 单文件输出：选一个最合适的 war
                        File chosen = chooseBestWar(wars);
                        System.out.println("[PLAN] copy(auto) " + chosen.getAbsolutePath() + " -> " + to.getAbsolutePath());
                        copier.copy(chosen, to);
                    } else {
                        // 目录输出：全部 war 输出到目录（to 作为目录）
                        File toDir = new File(outDir, toStr);
                        for (int k = 0; k < wars.size(); k++) {
                            File war = wars.get(k);
                            File target = new File(toDir, war.getName());
                            System.out.println("[PLAN] copy(auto) " + war.getAbsolutePath() + " -> " + target.getAbsolutePath());
                            copier.copy(war, target);
                        }
                    }
                }

            }
        }
    }

    private static File chooseBestWar(List<File> wars) {
        // 策略：优先选择最后修改时间最新的 war
        File best = null;
        long bestTime = -1L;

        for (int i = 0; i < wars.size(); i++) {
            File w = wars.get(i);
            if (w == null) continue;
            long t = w.lastModified();
            if (best == null || t > bestTime) {
                best = w;
                bestTime = t;
            }
        }
        return best;
    }

}
