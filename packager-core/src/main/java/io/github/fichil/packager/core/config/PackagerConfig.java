package io.github.fichil.packager.core.config;

import java.util.List;
import java.util.Map;

public class PackagerConfig {

    private Map<String, JobConfig> jobs;
    private GlobalMaven maven;
    private java.util.Map<String, String> vars;

    public Map<String, JobConfig> getJobs() { return jobs; }
    public void setJobs(Map<String, JobConfig> jobs) { this.jobs = jobs; }
    public Map<String, String> getVars() { return vars; }
    public void setVars(Map<String, String> vars) { this.vars = vars; }

    public static class JobConfig {
        private List<RepoConfig> repos;
        private ArtifactsConfig artifacts;

        public List<RepoConfig> getRepos() { return repos; }
        public void setRepos(List<RepoConfig> repos) { this.repos = repos; }

        public ArtifactsConfig getArtifacts() { return artifacts; }
        public void setArtifacts(ArtifactsConfig artifacts) { this.artifacts = artifacts; }
    }

    public static class RepoConfig {
        private String name;
        private String path;
        private String branch;
        private String gitUrl;
        private String shallow;
        private MavenConfig maven;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }

        public String getGitUrl() { return gitUrl; }
        public void setGitUrl(String gitUrl) { this.gitUrl = gitUrl; }

        public String getShallow() { return shallow; }
        public void setShallow(String shallow) { this.shallow = shallow; }

        public MavenConfig getMaven() { return maven; }
        public void setMaven(MavenConfig maven) { this.maven = maven; }
    }

    public static class MavenConfig {
        private String workDir;
        private List<String> goals;

        public String getWorkDir() { return workDir; }
        public void setWorkDir(String workDir) { this.workDir = workDir; }

        public List<String> getGoals() { return goals; }
        public void setGoals(List<String> goals) { this.goals = goals; }
    }

    public static class ArtifactsConfig {
        private String outputDir;
        private List<ArtifactFile> files;

        public String getOutputDir() { return outputDir; }
        public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

        public List<ArtifactFile> getFiles() { return files; }
        public void setFiles(List<ArtifactFile> files) { this.files = files; }
    }

    public static class ArtifactFile {
        private String repo;
        private String from;
        private String to;

        public String getRepo() { return repo; }
        public void setRepo(String repo) { this.repo = repo; }

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }

        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
    }
    public GlobalMaven getMaven() { return maven; }
    public void setMaven(GlobalMaven maven) { this.maven = maven; }

    public static class GlobalMaven {
        private String executable;

        public String getExecutable() { return executable; }
        public void setExecutable(String executable) { this.executable = executable; }
    }

}
