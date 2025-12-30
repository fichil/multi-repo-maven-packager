package io.github.fichil.packager.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CompositeConfigLoader {

    public static class NamedJob {
        private final String displayName; // apps:xxx
        private final String jobName;     // xxx
        private final PackagerConfig mergedConfig;
        private final PackagerConfig.JobConfig job;

        public NamedJob(String displayName, String jobName, PackagerConfig mergedConfig, PackagerConfig.JobConfig job) {
            this.displayName = displayName;
            this.jobName = jobName;
            this.mergedConfig = mergedConfig;
            this.job = job;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getJobName() {
            return jobName;
        }

        public PackagerConfig getMergedConfig() {
            return mergedConfig;
        }

        public PackagerConfig.JobConfig getJob() {
            return job;
        }
    }

    /**
     * Load package.yml, then load includes (apps.yml/openapi.yml ...),
     * merge global maven/vars into each included config,
     * then flatten jobs into a single list.
     */
    public static List<NamedJob> loadFromPackageYml(File packageYml) throws Exception {
        if (packageYml == null || !packageYml.exists() || !packageYml.isFile()) {
            throw new IllegalArgumentException("package.yml not found: " + (packageYml == null ? "null" : packageYml.getAbsolutePath()));
        }

        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        PackageConfig pkg = om.readValue(packageYml, PackageConfig.class);

        if (pkg.getIncludes() == null || pkg.getIncludes().isEmpty()) {
            throw new IllegalArgumentException("package.yml must define includes");
        }

        // global defaults
        PackagerConfig.GlobalMaven globalMaven = pkg.getMaven();
        Map<String, String> globalVars = pkg.getVars() == null
                ? new HashMap<String, String>()
                : new HashMap<String, String>(pkg.getVars());

        List<NamedJob> out = new ArrayList<NamedJob>();

        File baseDir = packageYml.getParentFile();
        ConfigLoader cfgLoader = new ConfigLoader();

        for (int i = 0; i < pkg.getIncludes().size(); i++) {
            PackageConfig.IncludeConfig inc = pkg.getIncludes().get(i);

            String incName = inc.getName();
            String incPath = inc.getPath();

            if (incPath == null || incPath.trim().isEmpty()) {
                continue;
            }

            File incFile = baseDir == null ? new File(incPath) : new File(baseDir, incPath);
            PackagerConfig child = cfgLoader.load(incFile);

            // merge config: inherit global maven/vars when child missing
            PackagerConfig merged = new PackagerConfig();

            PackagerConfig.GlobalMaven childMaven = child.getMaven();
            merged.setMaven(childMaven != null ? childMaven : globalMaven);

            Map<String, String> mergedVars = new HashMap<String, String>(globalVars);
            if (child.getVars() != null) {
                mergedVars.putAll(child.getVars());
            }
            merged.setVars(mergedVars);

            Map<String, PackagerConfig.JobConfig> childJobs = child.getJobs();
            if (childJobs == null) {
                childJobs = new LinkedHashMap<String, PackagerConfig.JobConfig>();
            }
            merged.setJobs(childJobs);

            if (merged.getJobs().isEmpty()) {
                continue;
            }

            for (Map.Entry<String, PackagerConfig.JobConfig> e : merged.getJobs().entrySet()) {
                String jobName = e.getKey();
                PackagerConfig.JobConfig job = e.getValue();

                String display;
                if (incName == null || incName.trim().isEmpty()) {
                    display = jobName;
                } else {
                    display = incName + ":" + jobName;
                }

                out.add(new NamedJob(display, jobName, merged, job));
            }
        }

        return out;
    }
}
