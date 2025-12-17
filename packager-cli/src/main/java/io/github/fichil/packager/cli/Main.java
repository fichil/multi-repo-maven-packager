package io.github.fichil.packager.cli;

import io.github.fichil.packager.core.artifact.ArtifactCopier;
import io.github.fichil.packager.core.config.ConfigLoader;
import io.github.fichil.packager.core.config.PackagerConfig;
import io.github.fichil.packager.core.exec.ProcessExecutor;
import io.github.fichil.packager.core.git.GitExecutor;
import io.github.fichil.packager.core.job.JobRunner;
import io.github.fichil.packager.core.maven.MavenExecutor;

import java.io.File;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws Exception {
        String conf = argValue(args, "-conf");
        String jobName = argValue(args, "-job");
        boolean skipTests = hasFlag(args, "--skipTests");

        if (conf == null || jobName == null) {
            System.err.println("Usage: -conf <path.yml> -job <jobName> [--skipTests]");
            System.exit(2);
            return;
        }

        // 1. 加载配置
        PackagerConfig config = new ConfigLoader().load(new File(conf));

        // 2. 取 job
        PackagerConfig.JobConfig job =
                config.getJobs() != null ? config.getJobs().get(jobName) : null;

        if (job == null) {
            System.err.println("Job not found: " + jobName);
            System.exit(3);
            return;
        }

        // 3. Maven executable
        String mvnExe = null;
        if (config.getMaven() != null) {
            mvnExe = config.getMaven().getExecutable();
        }

        // 4. vars（允许为空）
        Map<String, String> vars = config.getVars();

        // 5. 执行
        ProcessExecutor pe = new ProcessExecutor();
        JobRunner runner = new JobRunner(
                new GitExecutor(pe),
                new MavenExecutor(pe, mvnExe),
                new ArtifactCopier(),
                vars
        );

        runner.runJob(job, skipTests);

        System.out.println("DONE: " + jobName);
    }

    private static String argValue(String[] args, String key) {
        if (args == null) return null;
        for (int i = 0; i < args.length; i++) {
            if (key.equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static boolean hasFlag(String[] args, String flag) {
        if (args == null) return false;
        for (String arg : args) {
            if (flag.equals(arg)) return true;
        }
        return false;
    }
}
