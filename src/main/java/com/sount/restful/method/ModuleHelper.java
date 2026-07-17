package com.sount.restful.method;

import com.intellij.openapi.module.Module;
import com.sount.restful.configuration.SpringConfigurationReader;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 模块级 URL 前缀构建工具。
 * <p>
 * 根据模块的 {@code server.port} 和 {@code server.context-path} 配置
 * 拼接完整的服务地址前缀（如 {@code http://localhost:8080/api}）。
 * 配置读取委托给 {@link SpringConfigurationReader}。
 */
public class ModuleHelper {
    Module module;

    private static final String SCHEME = "http://";
    private static final String HOST = "localhost";
    private static final String PORT = "8080";
    public static String DEFAULT_URI = "http://localhost" + ":" + PORT;

    SpringConfigurationReader configurationReader;

    public ModuleHelper(Module module) {
        this.module = module;
        configurationReader = new SpringConfigurationReader(module);
    }

    public static ModuleHelper create(Module module) {
        return new ModuleHelper(module);
    }

    @NotNull
    public String getServiceHostPrefix() {
        if (module == null) {
            return DEFAULT_URI;
        }

        String port = configurationReader.getServerPort();
        if (StringUtils.isEmpty(port)) port = PORT;

        String contextPath = configurationReader.getContextPath();
        return SCHEME + HOST + ":" + port + contextPath;
    }

    @NotNull
    public String getContextPath() {
        return module == null ? "" : configurationReader.getContextPath();
    }
}
