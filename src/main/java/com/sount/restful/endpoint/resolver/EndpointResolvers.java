package com.sount.restful.endpoint.resolver;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 端点解析统一门面，隐藏策略装配和组合细节。
 */
public final class EndpointResolvers {
    private EndpointResolvers() {
    }

    /**
     * 解析指定模块的全部受支持端点。
     *
     * <p>本次调用中的所有策略共享同一模块配置快照，返回后快照即可释放。</p>
     */
    public static @NotNull List<RestServiceItem> resolve(@NotNull Module module) {
        return EndpointResolverRegistry.forModule(module).resolveModuleEndpoints();
    }

    /**
     * 解析指定项目的全部受支持端点。
     *
     * <p>策略按 Spring、JAX-RS 顺序执行，重复端点保留首个结果。</p>
     */
    public static @NotNull List<RestServiceItem> resolve(@NotNull Project project) {
        return EndpointResolverRegistry.forProject(project).resolveProjectEndpoints();
    }
}
