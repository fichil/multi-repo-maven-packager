package io.github.fichil.packager.core.config;

import java.util.List;
import java.util.Map;

/**
 * package.yml root model:
 * - maven: { executable: ... }  -> PackagerConfig.GlobalMaven
 * - vars:  { ... }             -> Map<String,String>
 * - includes:                  -> List<IncludeConfig>
 */
public class PackageConfig {

    private PackagerConfig.GlobalMaven maven;
    private Map<String, String> vars;
    private List<IncludeConfig> includes;

    public PackagerConfig.GlobalMaven getMaven() {
        return maven;
    }

    public void setMaven(PackagerConfig.GlobalMaven maven) {
        this.maven = maven;
    }

    public Map<String, String> getVars() {
        return vars;
    }

    public void setVars(Map<String, String> vars) {
        this.vars = vars;
    }

    public List<IncludeConfig> getIncludes() {
        return includes;
    }

    public void setIncludes(List<IncludeConfig> includes) {
        this.includes = includes;
    }

    public static class IncludeConfig {
        private String name;
        private String path;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
