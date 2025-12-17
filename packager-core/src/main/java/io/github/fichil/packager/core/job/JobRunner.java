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

    public JobRunner(GitExecutor git,
                     MavenExecutor mvn,
                     ArtifactCopier copier,
                     Map<String, String> vars) {
        this.git = git;
        this.mvn = mvn;
        this.copier = copier;
        this.vars = vars;
    }

    public void runJob(PackagerConfig.JobConfig job, boolean skipTests) throws Exception {
        if (job == null) {
            throw new IllegalArgumentException("Job is null");
        }

        Map<String, File> repoNameToDir = new HashMap<String, File>();

        // 1) 先执行 repos：git + mvn
        List<PackagerConfig.RepoConfig> repos = job.getRepos();
        if (repos != null) {
            for (int i = 0; i < repos.size(); i++) {
                PackagerConfig.RepoConfig r = repos.get(i);

                String repoPath = VarResolver.resolve(r.getPath(), vars);
                String branch = VarResolver.resolve(r.getBranch(), vars);

                File repoDir = new File(repoPath);
                if (!repoDir.exists() || !repoDir.isDirectory()) {
                    throw new IllegalArgumentException("Repo path not found: " + repoPath + " (repo=" + r.getName() + ")");
                }
                if (branch == null || branch.trim().isEmpty()) {
                    throw new IllegalArgumentException("Branch is empty: repo=" + r.getName());
                }

                repoNameToDir.put(r.getName(), repoDir);

                git.checkoutAndPull(repoDir, branch);

                String workDir = r.getMaven() != null ? r.getMaven().getWorkDir() : ".";
                workDir = VarResolver.resolve(workDir, vars);

                File mvnDir = ".".equals(workDir) ? repoDir : new File(repoDir, workDir);
                List<String> goals = r.getMaven() != null ? r.getMaven().getGoals() : null;

                mvn.runGoals(mvnDir, goals, skipTests);
            }
        }

        // 2) 再复制 artifacts
        PackagerConfig.ArtifactsConfig artifacts = job.getArtifacts();
        if (artifacts != null && artifacts.getFiles() != null) {

            String outDirStr = VarResolver.resolve(artifacts.getOutputDir(), vars);
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

                copier.copy(from, to);
            }
        }
    }
}
