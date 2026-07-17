package com.sount.restful.configuration;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Java properties 配置格式解析策略。
 */
final class PropertiesConfigurationParser implements ConfigurationFileParser {
    private static final Logger LOG = Logger.getInstance(PropertiesConfigurationParser.class);

    /**
     * 返回 properties 文件扩展名。
     */
    @Override
    public @NotNull String extension() {
        return "properties";
    }

    /**
     * 使用 JDK {@link Properties} 语义解析文本，并转换为统一键值映射。
     */
    @Override
    public @NotNull Map<String, Object> parse(@NotNull String text) {
        Properties properties = new Properties();
        try (StringReader reader = new StringReader(text)) {
            properties.load(reader);
        } catch (IOException e) {
            LOG.warn("Failed to load properties text", e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            result.put(name, properties.getProperty(name));
        }
        return result;
    }
}
