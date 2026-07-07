package com.sount.restful.method;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring Boot 配置文件读取工具。
 * <p>
 * 按优先级扫描 {@code application} / {@code bootstrap} 配置文件
 * （支持 {@code .properties} 和 {@code .yml} 格式），读取指定属性值。
 * 支持 {@code spring.profiles.active} 多环境配置切换。
 */
public class PropertiesHandler {
    private static final Logger LOG = Logger.getInstance(PropertiesHandler.class);

    public String[] getFileExtensions() {
        return new String[]{"properties", "yml"};
    }

    public String[] getConfigFiles() {
        return new String[]{"application", "bootstrap"};
    }

    String SPRING_PROFILE = "spring.profiles.active";

    String placeholderPrefix = "${";
    String valueSeparator = ":";
    String placeholderSuffix = "}";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]*)}");

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

        if (activeProfile != null) {
            propertyValue = findPropertyValue(propertyKey, activeProfile);
        }
        if (propertyValue == null) {
            propertyValue = findPropertyValue(propertyKey, null);
        }

        return propertyValue != null ? propertyValue : "";
    }

    private String findProfilePropertyValue() {
        return findPropertyValue(SPRING_PROFILE, null);
    }

    private String findPropertyValue(String propertyKey, String activeProfile) {
        String value;
        String profile = activeProfile != null ? "-" + activeProfile : "";

        for (String conf : getConfigFiles()) {
            for (String ext : getFileExtensions()) {
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
        try (StringReader reader = new StringReader(text)) {
            prop.load(reader);
        } catch (IOException e) {
            LOG.warn("Failed to load properties text", e);
        }
        return prop;
    }

    public String getContextPath() {
        return getProperty("server.context-path");
    }

    String cleanPlaceholderIfExist(String value) {
        if (value == null || !value.contains(placeholderPrefix)) {
            return value;
        }
        // Resolve Spring-style placeholders ${key:defaultValue} inline. The default value is
        // everything after the FIRST colon (matching Spring's resolution), so ${a:b:c} -> "b:c".
        // Placeholders without a default (${key}) cannot be resolved from a single source and
        // are left intact with a warning, rather than being silently mangled.
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1);
            int colonIndex = content.indexOf(':');
            String replacement;
            if (colonIndex >= 0) {
                replacement = content.substring(colonIndex + 1);
            } else {
                LOG.warn("Unresolved property placeholder '" + matcher.group(0) + "' in value");
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Map<String, Object> getPropertiesMapFromYamlFile(String configFile) {
        PsiFile applicationPropertiesFile = findPsiFileInModule(configFile);
        if (applicationPropertiesFile != null) {
            Yaml yaml = new Yaml();
            String yamlText = applicationPropertiesFile.getText();
            try {
                Map<String, Object> ymlPropertiesMap = yaml.load(yamlText);
                return getFlattenedMap(ymlPropertiesMap);
            } catch (Exception e) {
                return null;
            }
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
