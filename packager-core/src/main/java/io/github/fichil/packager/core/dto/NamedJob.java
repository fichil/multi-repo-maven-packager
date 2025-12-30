package io.github.fichil.packager.core.dto;

import io.github.fichil.packager.core.config.PackagerConfig;

public class NamedJob {
    public final String displayName;   // apps:wms-Develop
    public final String jobName;
    public final PackagerConfig config;
    public final PackagerConfig.JobConfig job;

    public NamedJob(String displayName, String jobName,
                    PackagerConfig config, PackagerConfig.JobConfig job) {
        this.displayName = displayName;
        this.jobName = jobName;
        this.config = config;
        this.job = job;
    }
}

