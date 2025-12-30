package io.github.fichil.packager.cli;

import io.github.fichil.packager.core.artifact.ArtifactCopier;
import io.github.fichil.packager.core.config.ConfigLoader;
import io.github.fichil.packager.core.config.PackagerConfig;
import io.github.fichil.packager.core.exec.ProcessExecutor;
import io.github.fichil.packager.core.git.GitExecutor;
import io.github.fichil.packager.core.job.JobRunner;
import io.github.fichil.packager.core.maven.MavenExecutor;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        String conf = argValue(args, "-conf");
        boolean listJobs = hasFlag(args, "--list-jobs");

        // flags: 如果命令行没传就交互问
        Boolean skipTests = hasFlag(args, "--skipTests") ? Boolean.TRUE : null;
        Boolean dryRun = hasFlag(args, "--dry-run") ? Boolean.TRUE : null;

        String jobName = argValue(args, "-job");

        // conf
        if (isBlank(conf)) {
            System.out.print("Config file path (-conf) [Enter=default]: ");
            conf = sc.nextLine();
            if (conf != null && conf.trim().isEmpty()) {
                conf = new File(System.getProperty("user.dir"), "config-examples/packager.sample.yml").getPath();
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

        // job choose
        List<String> jobNames = null;
        if (isBlank(jobName)) {
            jobNames = printJobsWithIndex(config);
            System.out.print("Job (number or name) (-job): ");
            jobName = sc.nextLine();
        }
        jobName = trimQuotes(jobName);
        jobName = normalizeJobName(jobName, jobNames);

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
            // 你的工具场景：默认跳过测试（Y）
            skipTests = askYesNo(sc, "Skip tests? (--skipTests) [Y/n]: ", true);
        }
        if (dryRun == null) {
            // 安全起见：默认 dry-run（Y）
            dryRun = askYesNo(sc, "Dry run? (--dry-run) [Y/n]: ", true);
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
        System.out.println("DONE: " + jobName + (dryRun.booleanValue() ? " (DRY-RUN)" : ""));
    }

    private static boolean askYesNo(Scanner sc, String prompt, boolean defaultValue) {
        System.out.print(prompt);
        String s = sc.nextLine();
        if (s == null) return defaultValue;

        s = s.trim().toLowerCase();
        if (s.isEmpty()) return defaultValue;

        if ("y".equals(s) || "yes".equals(s) || "true".equals(s) || "1".equals(s)) return true;
        if ("n".equals(s) || "no".equals(s) || "false".equals(s) || "0".equals(s)) return false;

        // 非法输入：回落默认值（也可以改成循环重新输入）
        return defaultValue;
    }

    private static List<String> printJobsWithIndex(PackagerConfig config) {
        if (config.getJobs() == null || config.getJobs().isEmpty()) {
            System.out.println("No jobs found.");
            return java.util.Collections.emptyList();
        }

        List<String> names = new java.util.ArrayList<String>(config.getJobs().keySet());
        java.util.Collections.sort(names); // 顺序稳定

        System.out.println("Available jobs:");
        for (int i = 0; i < names.size(); i++) {
            System.out.println((i + 1) + ") " + names.get(i));
        }
        return names;
    }

    private static String normalizeJobName(String input, List<String> jobNames) {
        if (isBlank(input)) return input;

        String s = input.trim();
        if (jobNames != null && !jobNames.isEmpty() && s.matches("\\d+")) {
            int idx = Integer.parseInt(s);
            if (idx >= 1 && idx <= jobNames.size()) {
                return jobNames.get(idx - 1);
            }
        }
        return s;
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
}
