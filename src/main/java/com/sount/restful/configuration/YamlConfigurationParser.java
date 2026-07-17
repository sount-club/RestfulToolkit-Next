package com.sount.restful.configuration;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring YAML 配置格式解析策略，负责把嵌套结构转换为点分隔键。
 */
final class YamlConfigurationParser implements ConfigurationFileParser {

    /**
     * 返回当前既有功能支持的 yml 文件扩展名。
     */
    @Override
    public @NotNull String extension() {
        return "yml";
    }

    /**
     * 解析 YAML 文本并扁平化；空文档或语法错误返回 {@code null}。
     */
    @Override
    public @Nullable Map<String, Object> parse(@NotNull String text) {
        try {
            Object loaded = new Yaml().load(text);
            if (!(loaded instanceof Map<?, ?> source)) {
                return null;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            buildFlattenedMap(result, toStringObjectMap(source), null);
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 递归展开 map 和 collection，保留原先的点路径与数组下标格式。
     */
    private void buildFlattenedMap(@NotNull Map<String, Object> result,
                                   @NotNull Map<String, Object> source,
                                   @Nullable String path) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (StringUtils.isNotBlank(path)) {
                key = key.startsWith("[") ? path + key : path + '.' + key;
            }

            Object value = entry.getValue();
            if (value == null) {
                result.put(key, "");
            } else if (value instanceof Map<?, ?> mapValue) {
                buildFlattenedMap(result, toStringObjectMap(mapValue), key);
            } else if (value instanceof Collection<?> collection) {
                int index = 0;
                for (Object item : collection) {
                    buildFlattenedMap(result,
                            Collections.singletonMap("[" + index++ + "]", item), key);
                }
            } else {
                result.put(key, value);
            }
        }
    }

    /**
     * 将 SnakeYAML 产生的任意键类型统一转换为字符串键。
     */
    private @NotNull Map<String, Object> toStringObjectMap(@NotNull Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
