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
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        String conf = argValue(args, "-conf");
        boolean listJobs = hasFlag(args, "--list-jobs");

        Boolean skipTests = hasFlag(args, "--skipTests") ? Boolean.TRUE : null;
        Boolean dryRun = hasFlag(args, "--dry-run") ? Boolean.TRUE : null;

        String jobName = argValue(args, "-job");

        // conf
        if (isBlank(conf)) {
            System.out.print("Config file path (-conf): ");
            conf = sc.nextLine();
            if (conf.isEmpty()){
                conf = "config-examples/packager.sample.yml";
            }
        }
        conf = trimQuotes(conf);
        if (isBlank(conf)) {
            System.err.println("Config file path is required.");
            System.exit(2);
            return;
        }

        PackagerConfig config = new ConfigLoader().load(new File(conf));

        // list jobs
        if (listJobs) {
            printJobsWithIndex(config);
            return;
        }

        // job
        java.util.List<String> jobNames = null;

        if (isBlank(jobName)) {
            jobNames = printJobsWithIndex(config);
            System.out.print("Job (number or name) (-job): ");
            jobName = sc.nextLine();
        }
        jobName = trimQuotes(jobName);

        // 如果输入的是数字：映射到 job 名称
        jobName = normalizeJobName(jobName, jobNames);

        jobName = trimQuotes(jobName);
        if (isBlank(jobName)) {
            System.err.println("Job name is required.");
            System.exit(2);
            return;
        }


        PackagerConfig.JobConfig job = config.getJobs() != null ? config.getJobs().get(jobName) : null;
        if (job == null) {
            System.err.println("Job not found: " + jobName);
            System.err.println("Hint: use --list-jobs to view available jobs.");
            System.exit(3);
            return;
        }

        // flags（没传就问）
        if (skipTests == null) {
            skipTests = askYesNo(sc, "Skip tests? (--skipTests) [Y/n]: ", false);
        }
        if (dryRun == null) {
            dryRun = askYesNo(sc, "Dry run? (--dry-run) [y/N]: ", false);
        }

        String mvnExe = config.getMaven() != null ? config.getMaven().getExecutable() : null;
        Map<String, String> vars = config.getVars();

        ProcessExecutor pe = new ProcessExecutor();
        pe.setDryRun(dryRun.booleanValue());

        JobRunner runner = new JobRunner(
                new GitExecutor(pe),
                new MavenExecutor(pe, mvnExe),
                new ArtifactCopier(dryRun.booleanValue()),
                vars,
                dryRun.booleanValue()
        );

        runner.runJob(job, skipTests.booleanValue());
        System.out.println("DONE: " + jobName + (dryRun ? " (DRY-RUN)" : ""));
    }

    private static boolean askYesNo(Scanner sc, String prompt, boolean defaultValue) {
        System.out.print(prompt);
        String s = sc.nextLine();
        if (s == null) return defaultValue;
        s = s.trim().toLowerCase();
        if (s.isEmpty()) return defaultValue;
        return "y".equals(s) || "yes".equals(s) || "true".equals(s) || "1".equals(s);
    }


    private static java.util.List<String> printJobsWithIndex(PackagerConfig config) {
        if (config.getJobs() == null || config.getJobs().isEmpty()) {
            System.out.println("No jobs found.");
            return java.util.Collections.emptyList();
        }

        java.util.List<String> names = new java.util.ArrayList<String>(config.getJobs().keySet());

        java.util.Collections.sort(names);

        System.out.println("Available jobs:");
        for (int i = 0; i < names.size(); i++) {
            System.out.println((i + 1) + ") " + names.get(i));
        }
        return names;
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
        for (int i = 0; i < args.length; i++) {
            if (flag.equals(args[i])) return true;
        }
        return false;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // 兼容用户复制粘贴带引号路径： "D:\a\b.yml"
    private static String trimQuotes(String s) {
        if (s == null) return null;
        s = s.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String normalizeJobName(String input, java.util.List<String> jobNames) {
        if (isBlank(input)) return input;

        String s = input.trim();
        // 只有在我们确实打印过列表时才支持数字选择
        if (jobNames != null && !jobNames.isEmpty() && s.matches("\\d+")) {
            int idx = Integer.parseInt(s);
            if (idx >= 1 && idx <= jobNames.size()) {
                return jobNames.get(idx - 1);
            }
            // 数字越界就原样返回，后面会走 job not found 的报错提示
        }
        return s;
    }

}
