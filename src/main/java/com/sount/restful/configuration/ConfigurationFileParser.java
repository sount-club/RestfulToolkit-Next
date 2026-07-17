package com.sount.restful.configuration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Spring 配置文件格式解析策略。
 */
interface ConfigurationFileParser {

    /**
     * 返回该策略处理的文件扩展名，不包含点号。
     */
    @NotNull String extension();

    /**
     * 把配置文本解析为扁平键值映射；格式无效时返回 {@code null} 以触发后续文件回退。
     */
    @Nullable Map<String, Object> parse(@NotNull String text);
}
