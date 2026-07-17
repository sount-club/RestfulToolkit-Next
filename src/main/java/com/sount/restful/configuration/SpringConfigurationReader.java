package com.sount.restful.configuration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring Boot 模块配置读取器。
 *
 * <p>按 application → bootstrap、properties → yml 的既有优先级查找属性，
 * 并在存在 active profile 时优先读取 profile 文件。</p>
 */
public final class SpringConfigurationReader {
    private static final Logger LOG = Logger.getInstance(SpringConfigurationReader.class);
    private static final String SPRING_PROFILE = "spring.profiles.active";
    private static final String PLACEHOLDER_PREFIX = "${";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]*)}");
    private static final List<String> CONFIG_FILES = List.of("application", "bootstrap");

    private final Module module;
    private final List<ConfigurationFileParser> parsers;

    /**
     * 创建读取器并按原有格式优先级注册 properties 与 yml 解析策略。
     */
    public SpringConfigurationReader(@NotNull Module module) {
        this(module, List.of(new PropertiesConfigurationParser(), new YamlConfigurationParser()));
    }

    /**
     * 创建使用指定解析策略顺序的读取器，供同包测试和未来格式扩展使用。
     */
    SpringConfigurationReader(@NotNull Module module, @NotNull List<ConfigurationFileParser> parsers) {
        this.module = module;
        this.parsers = List.copyOf(parsers);
    }

    /**
     * 返回当前启用的配置文件扩展名，顺序即属性查找优先级。
     */
    public String @NotNull [] getFileExtensions() {
        return parsers.stream().map(ConfigurationFileParser::extension).toArray(String[]::new);
    }

    /**
     * 返回当前启用的 Spring 配置文件基础名称。
     */
    public String @NotNull [] getConfigFiles() {
        return CONFIG_FILES.toArray(String[]::new);
    }

    /**
     * 读取 server.port，未配置时返回空字符串。
     */
    public @NotNull String getServerPort() {
        return getProperty("server.port");
    }

    /**
     * 按 profile 与文件优先级读取属性，并统一处理 Spring 内联默认占位符。
     */
    public @NotNull String getProperty(@NotNull String propertyKey) {
        String activeProfile = findPropertyValue(SPRING_PROFILE, null);
        String propertyValue = activeProfile != null
                ? findPropertyValue(propertyKey, activeProfile)
                : null;
        if (propertyValue == null) {
            propertyValue = findPropertyValue(propertyKey, null);
        }
        return propertyValue != null ? propertyValue : "";
    }

    /**
     * 按 servlet 新配置优先、legacy 配置兜底的规则读取 context-path。
     */
    public @NotNull String getContextPath() {
        String servletContextPath = getProperty("server.servlet.context-path");
        return servletContextPath.isEmpty() ? getProperty("server.context-path") : servletContextPath;
    }

    /**
     * 遍历配置文件与解析策略，返回第一个包含目标属性的值。
     */
    private @Nullable String findPropertyValue(@NotNull String propertyKey, @Nullable String activeProfile) {
        String profileSuffix = activeProfile != null ? "-" + activeProfile : "";
        for (String configBaseName : CONFIG_FILES) {
            for (ConfigurationFileParser parser : parsers) {
                String configFile = configBaseName + profileSuffix + "." + parser.extension();
                PsiFile psiFile = findPsiFileInModule(configFile);
                if (psiFile == null) {
                    continue;
                }
                Map<String, Object> values = parser.parse(psiFile.getText());
                if (values == null) {
                    continue;
                }
                Object value = values.get(propertyKey);
                if (value != null) {
                    return value instanceof String stringValue
                            ? cleanPlaceholderIfExist(stringValue)
                            : value.toString();
                }
            }
        }
        return null;
    }

    /**
     * 解析 Spring 风格的内联默认值；无默认值的占位符保持原样并记录警告。
     */
    @Nullable String cleanPlaceholderIfExist(@Nullable String value) {
        if (value == null || !value.contains(PLACEHOLDER_PREFIX)) {
            return value;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
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
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 在当前模块 scope 中查找同名配置文件，并保持原先“首个匹配文件”语义。
     */
    private @Nullable PsiFile findPsiFileInModule(@NotNull String fileName) {
        Collection<VirtualFile> virtualFiles = FilenameIndex.getVirtualFilesByName(
                fileName, GlobalSearchScope.moduleScope(module));
        if (virtualFiles.isEmpty()) {
            return null;
        }
        return PsiManager.getInstance(module.getProject()).findFile(virtualFiles.iterator().next());
    }
}
