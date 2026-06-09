package com.sount.restful.method.action;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
// profile.active != null  // YamlPropertySourceLoader extends PropertySourceLoader .load

// PropertySourcesLoader.load 配置文件加载类
// 路径location：[file:./config/, file:./, classpath:/config/, classpath:/]
//文件name：bootstrap，application
//后缀：[properties, XML, YML, YAML]
//applicationConfig: [classpath:/application.yml]#prod

// 如果存在 activeProfile spring.profiles.active 存在，判断是否存在 application-activeProfile. 文件，
// 如果存在，判断是否存在设置，不存在则忽略
//最终可能优先级 application.properties>application.yml>bootstrap.propertis>bootstrap.yml
//路径：classpath:/(resource)>classpath:/config/
public class PropertiesHandler {
    private static final Logger LOG = Logger.getInstance(PropertiesHandler.class);

    public String[] getFileExtensions() { //优先级
        return new String[]{"properties", "yml"};
    }

    public String[] getConfigFiles() { // 优先级
        return new String[]{"application", "bootstrap"};
    }

    String SPRING_PROFILE = "spring.profiles.active";

    String placeholderPrefix = "${";
    String valueSeparator = ":";
    String placeholderSuffix = "}";

    String activeProfile;

    Module module;

    public PropertiesHandler(Module module) {
        this.module = module;
    }

    public String getServerPort() {
        return getProperty("server.port");
    }

    public String getProperty(String propertyKey) {
        String propertyValue = null;

        activeProfile = findProfilePropertyValue();

        //
        if (activeProfile != null) {
            propertyValue = findPropertyValue(propertyKey, activeProfile);
        }
        if (propertyValue == null) {
            propertyValue = findPropertyValue(propertyKey, null);
        }

        return propertyValue != null ? propertyValue : "";
    }


    /* try to find spring.profiles.active value */
    private String findProfilePropertyValue() {
        return findPropertyValue(SPRING_PROFILE, null);
    }

    /* 暂时不考虑路径问题，默认找到的第一文件 */
    private String findPropertyValue(String propertyKey, String activeProfile) {
        String value;
        String profile = activeProfile != null ? "-" + activeProfile : "";
        //
        for (String conf : getConfigFiles()) {
            for (String ext : getFileExtensions()) {
                // load spring config file
                String configFile = conf + profile + "." + ext;
                if (ext.equals("properties")) {
                    Properties properties = loadPropertiesFromConfigFile(configFile);
                    if (properties != null) {
                        String valueObj = properties.getProperty(propertyKey);
                        if (valueObj != null) {
                            value = cleanPlaceholderIfExist(valueObj);
                            return value;
                        }
                    }

                } else if (ext.equals("yml") || ext.equals("yaml")) {
                    Map<String, Object> propertiesMap = getPropertiesMapFromYamlFile(configFile);
                    if (propertiesMap != null) {
                        Object valueObj = propertiesMap.get(propertyKey);
                        if (valueObj == null) return null;

                        if (valueObj instanceof String) {
                            value = cleanPlaceholderIfExist((String) valueObj);
                        } else {
                            value = valueObj.toString();
                        }
                        return value;
                    }
                }
            }
        }

        return null;
    }

    private Properties loadPropertiesFromConfigFile(String configFile) {
        Properties properties = null;
        PsiFile applicationPropertiesFile = findPsiFileInModule(configFile);
        if (applicationPropertiesFile != null) {
            properties = loadPropertiesFromText(applicationPropertiesFile.getText());
        }
        return properties;
    }

    @NotNull
    private Properties loadPropertiesFromText(String text) {
        Properties prop = new Properties();
        try {
            prop.load(new StringReader(text));
        } catch (IOException e) {
            LOG.warn("Failed to load properties text", e);
        }
        return prop;
    }

    public String getContextPath() {
        return getProperty("server.context-path");
    }

    private String cleanPlaceholderIfExist(String value) {
        // server.port=${PORT:8080}
        if (value != null && value.contains(placeholderPrefix) && value.contains(valueSeparator)) {
            String[] split = value.split(valueSeparator);


            if (split.length > 1) {
                value = split[1].replace(placeholderSuffix, "");
            }
//            value = value.replace(placeholderPrefix,"").replace(placeholderSuffix,"");
        }
        return value;
    }


    private Map<String, Object> getPropertiesMapFromYamlFile(String configFile) {
        PsiFile applicationPropertiesFile = findPsiFileInModule(configFile);
        if (applicationPropertiesFile != null) {
            Yaml yaml = new Yaml();

            String yamlText = applicationPropertiesFile.getText();
            try {
                Map<String, Object> ymlPropertiesMap = yaml.load(yamlText);
                return getFlattenedMap(ymlPropertiesMap);
            } catch (Exception e) { // FIXME: spring 同一个文件中配置多个环境时； yaml 格式不规范，比如包含 "---"

                return null;
            }

//        Object yamlProperty = getYamlProperty(key, ymlPropertiesMap);
        }
        return null;
    }


    private @Nullable PsiFile findPsiFileInModule(String fileName) {
        Collection<VirtualFile> virtualFiles = FilenameIndex.getVirtualFilesByName(
                fileName,
                GlobalSearchScope.moduleScope(module));
        if (!virtualFiles.isEmpty()) {
            VirtualFile vf = virtualFiles.iterator().next();
            return PsiManager.getInstance(module.getProject()).findFile(vf);
        }
        return null;
    }

    /**
     * ref: org.springframework.beans.factory.config.YamlProcessor
     */
    protected final Map<String, Object> getFlattenedMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        this.buildFlattenedMap(result, source, null);
        return result;
    }

    private void buildFlattenedMap(Map<String, Object> result, Map<String, Object> source, String path) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (StringUtils.isNotBlank(path)) {
                if (key.startsWith("[")) {
                    key = path + key;
                } else {
                    key = path + '.' + key;
                }
            }

            Object value = entry.getValue();
            if (value == null) {
                result.put(key, "");
            } else {
                switch (value) {
                    case Map<?, ?> mapValue -> this.buildFlattenedMap(result, toStringObjectMap(mapValue), key);
                    case Collection<?> collection -> {
                        int count = 0;

                        for (Object object : collection) {
                            this.buildFlattenedMap(result, Collections.singletonMap("[" + count++ + "]", object), key);
                        }
                    }
                    default -> result.put(key, value);
                }
            }
        }
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

}
