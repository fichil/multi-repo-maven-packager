package io.github.fichil.packager.cli;

import io.github.fichil.packager.core.artifact.ArtifactCopier;
import io.github.fichil.packager.core.config.CompositeConfigLoader;
import io.github.fichil.packager.core.config.CompositeConfigLoader.NamedJob;
import io.github.fichil.packager.core.config.PackagerConfig;
import io.github.fichil.packager.core.exec.ProcessExecutor;
import io.github.fichil.packager.core.git.GitExecutor;
import io.github.fichil.packager.core.job.JobRunner;
import io.github.fichil.packager.core.maven.MavenExecutor;
import io.github.fichil.packager.core.artifact.ArtifactFinder;


import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * CLI entry:
 * - default config: ./package.yml
 * - load jobs from package.yml includes (apps.yml/openapi.yml...)
 * - print merged jobs with index
 * - support multi-select: 1\3\5 (execute in input order)
 *
 * Notes:
 * - no lambda (project constraint)
 */
public class Main {

    private static final int EXIT_CONF_REQUIRED = 2;
    private static final int EXIT_JOB_REQUIRED = 2;
    private static final int EXIT_JOB_NOT_FOUND = 3;

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        String conf = trimQuotes(argValue(args, "-conf"));
        boolean listJobs = hasFlag(args, "--list-jobs");

        // flags: 如果命令行没传就交互问
        Boolean skipTests = hasFlag(args, "--skipTests") ? Boolean.TRUE : null;
        Boolean dryRun = hasFlag(args, "--dry-run") ? Boolean.TRUE : null;

        String jobArg = trimQuotes(argValue(args, "-job")); // now supports: "1\3\5" or "openapi:xxx\apps:yyy"

        // 1) config path
        if (isBlank(conf)) {
            System.out.print("Config file path (-conf) [Enter=package.yml]: ");
            conf = sc.nextLine();
            if (conf == null || conf.trim().isEmpty()) {
                conf = new File(System.getProperty("user.dir"), "package.yml").getPath();
            }
        }
        conf = trimQuotes(conf);

        File confFile = new File(conf);
        if (!confFile.exists() || !confFile.isFile()) {
            System.err.println("Config file not found: " + confFile.getAbsolutePath());
            System.err.println("Hint: default config is ./package.yml");
            System.exit(EXIT_CONF_REQUIRED);
            return;
        }

        System.out.println("[INFO] Using config: " + confFile.getAbsolutePath());

        // 2) load jobs (from includes)
        List<NamedJob> allJobs = CompositeConfigLoader.loadFromPackageYml(confFile);
        if (allJobs == null || allJobs.isEmpty()) {
            System.out.println("No jobs found.");
            System.exit(EXIT_JOB_NOT_FOUND);
            return;
        }

        // 3) list jobs only
        if (listJobs) {
            printJobsWithIndex(allJobs);
            return;
        }

        // 4) choose jobs (multi-select)
        if (isBlank(jobArg)) {
            printJobsWithIndex(allJobs);
            System.out.print("Jobs (numbers or names, separated by '\\') (-job): ");
            jobArg = sc.nextLine();
        }
        jobArg = trimQuotes(jobArg);

        List<NamedJob> selectedJobs = resolveSelectedJobs(jobArg, allJobs);
        if (selectedJobs == null || selectedJobs.isEmpty()) {
            System.err.println("Job is required.");
            System.err.println("Hint: input like 1\\3\\5 or apps:wms-Develop\\openapi:openapi-tssb_develop");
            System.exit(EXIT_JOB_REQUIRED);
            return;
        }

        // 5) flags
        if (skipTests == null) {
            // 默认跳过测试（更适合打包工具）
            skipTests = askYesNo(sc, "Skip tests? (--skipTests) [Y/n]: ", true);
        }
        if (dryRun == null) {
            // 默认 dry-run，避免误操作
            dryRun = askYesNo(sc, "Dry run? (--dry-run) [Y/n]: ", true);
        }

        // 6) execute in input order
        for (int i = 0; i < selectedJobs.size(); i++) {
            NamedJob sel = selectedJobs.get(i);

            System.out.println("==================================================");
            System.out.println("[" + (i + 1) + "/" + selectedJobs.size() + "] RUN: " + sel.getDisplayName());
            System.out.println("==================================================");

            PackagerConfig cfg = sel.getMergedConfig();
            PackagerConfig.JobConfig job = sel.getJob();

            String mvnExe = cfg.getMaven() != null ? cfg.getMaven().getExecutable() : null;
            Map<String, String> vars = cfg.getVars();

            ProcessExecutor pe = new ProcessExecutor();
            pe.setDryRun(dryRun.booleanValue());

            JobRunner runner = new JobRunner(
                    new GitExecutor(pe),
                    new MavenExecutor(pe, mvnExe),
                    new ArtifactFinder(),
                    new ArtifactCopier(dryRun.booleanValue()),
                    vars,
                    dryRun.booleanValue()
            );

            runner.runJob(job, skipTests.booleanValue());
            System.out.println("DONE: " + sel.getDisplayName() + (dryRun.booleanValue() ? " (DRY-RUN)" : ""));
        }
    }

    private static void printJobsWithIndex(List<NamedJob> allJobs) {
        System.out.println("Available jobs:");
        for (int i = 0; i < allJobs.size(); i++) {
            System.out.println((i + 1) + ") " + allJobs.get(i).getDisplayName());
        }
    }

    /**
     * Resolve user input to selected NamedJob list.
     * Supported input formats:
     * - numbers: "1\3\5"
     * - names  : "apps:wms-Develop\openapi:openapi-tssb_develop"
     * - mix    : "1\openapi:openapi-tssb_develop\3"
     *
     * Invalid items are ignored, but if all invalid -> return empty.
     */
    private static List<NamedJob> resolveSelectedJobs(String input, List<NamedJob> allJobs) {
        if (isBlank(input)) return java.util.Collections.emptyList();

        String[] parts = input.split("\\\\");
        List<NamedJob> selected = new ArrayList<NamedJob>();

        for (int i = 0; i < parts.length; i++) {
            String raw = parts[i];
            if (raw == null) continue;
            String token = raw.trim();
            if (token.isEmpty()) continue;

            NamedJob found = null;

            // number
            if (token.matches("\\d+")) {
                int idx = Integer.parseInt(token);
                if (idx >= 1 && idx <= allJobs.size()) {
                    found = allJobs.get(idx - 1);
                }
            } else {
                // name match: displayName or jobName
                found = findJobByName(token, allJobs);
            }

            if (found != null) {
                selected.add(found);
            }
        }

        return selected;
    }

    private static NamedJob findJobByName(String token, List<NamedJob> allJobs) {
        if (token == null) return null;

        // 1) exact match displayName
        for (int i = 0; i < allJobs.size(); i++) {
            NamedJob j = allJobs.get(i);
            if (token.equals(j.getDisplayName())) return j;
        }

        // 2) exact match jobName (may be ambiguous if includes contain same jobName)
        NamedJob candidate = null;
        for (int i = 0; i < allJobs.size(); i++) {
            NamedJob j = allJobs.get(i);
            if (token.equals(j.getJobName())) {
                if (candidate == null) {
                    candidate = j;
                } else {
                    // ambiguous
                    System.err.println("[WARN] job name is ambiguous, please use displayName with prefix: " + token);
                    return null;
                }
            }
        }
        if (candidate != null) return candidate;

        // 3) case-insensitive match displayName
        String lower = token.toLowerCase();
        for (int i = 0; i < allJobs.size(); i++) {
            NamedJob j = allJobs.get(i);
            if (j.getDisplayName() != null && j.getDisplayName().toLowerCase().equals(lower)) {
                return j;
            }
        }

        return null;
    }

    private static boolean askYesNo(Scanner sc, String prompt, boolean defaultValue) {
        System.out.print(prompt);
        String s = sc.nextLine();
        if (s == null) return defaultValue;

        s = s.trim().toLowerCase();
        if (s.isEmpty()) return defaultValue;

        if ("y".equals(s) || "yes".equals(s) || "true".equals(s) || "1".equals(s)) return true;
        if ("n".equals(s) || "no".equals(s) || "false".equals(s) || "0".equals(s)) return false;

        return defaultValue;
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
