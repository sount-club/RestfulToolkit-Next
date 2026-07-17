package com.sount.restful.search.application;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.sount.restful.search.domain.PathSearchOptions;
import org.jetbrains.annotations.NotNull;

/**
 * 项目级路径搜索设置适配器，隔离 IntelliJ 持久化 API 与纯路径匹配模型。
 */
public final class PathSearchSettings {
    public static final String GATEWAY_PREFIXES_KEY = "RestfulToolkit.Search.GatewayPrefixes";

    private PathSearchSettings() {
    }

    /**
     * 读取当前项目保存的网关前缀，并转换为不可变路径搜索选项。
     */
    public static @NotNull PathSearchOptions forProject(@NotNull Project project) {
        String value = PropertiesComponent.getInstance(project).getValue(GATEWAY_PREFIXES_KEY, "");
        return PathSearchOptions.parse(value);
    }
}
