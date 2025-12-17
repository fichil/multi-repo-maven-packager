package io.github.fichil.packager.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;

public class ConfigLoader {

    public PackagerConfig load(File yamlFile) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(yamlFile, PackagerConfig.class);
    }
}
