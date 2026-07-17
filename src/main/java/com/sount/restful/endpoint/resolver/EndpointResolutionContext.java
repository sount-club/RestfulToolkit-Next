package com.sount.restful.endpoint.resolver;

import com.intellij.openapi.module.Module;
import com.sount.restful.method.ModuleHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 保存单次端点解析过程共享的模块配置快照。
 *
 * <p><b>生命周期约束：</b>实例只归属于一次 resolver 聚合调用，禁止注册为 project service
 * 或保存到静态字段，从而保证下一次索引重建能读取最新配置。</p>
 * <p><b>线程约束：</b>端点解析在同一个 IntelliJ 读任务内串行执行，本类不提供跨线程同步。</p>
 */
final class EndpointResolutionContext {
    private final Map<Module, String> contextPaths = new HashMap<>();
    private final Function<Module, String> contextPathLoader;

    EndpointResolutionContext() {
        this(module -> new ModuleHelper(module).getContextPath());
    }

    EndpointResolutionContext(@NotNull Function<Module, String> contextPathLoader) {
        this.contextPathLoader = contextPathLoader;
    }

    /**
     * 返回模块在本次端点解析中的 context-path 快照。
     *
     * <p><b>性能约束：</b>同一模块只调用一次底层配置加载器；空值统一规范为字符串空值并参与缓存。</p>
     *
     * @param module 端点所属模块；为空时返回空 context-path
     * @return 本次解析期间稳定的 context-path
     */
    @NotNull String getContextPath(@Nullable Module module) {
        if (module == null) {
            return "";
        }
        return contextPaths.computeIfAbsent(module, key -> normalize(contextPathLoader.apply(key)));
    }

    /**
     * 将配置加载器可能返回的空值规范为可安全缓存的字符串。
     */
    private static @NotNull String normalize(@Nullable String contextPath) {
        return contextPath != null ? contextPath : "";
    }
}
