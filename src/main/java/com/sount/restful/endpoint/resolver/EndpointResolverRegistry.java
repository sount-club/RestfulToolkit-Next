package com.sount.restful.endpoint.resolver;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 按固定优先级装配端点解析策略。
 */
final class EndpointResolverRegistry {

    private EndpointResolverRegistry() {
    }

    /**
     * 创建模块范围的 resolver 列表，并让它们共享本次解析的模块配置快照。
     */
    static @NotNull CompositeEndpointResolver forModule(@NotNull Module module) {
        EndpointResolutionContext resolutionContext = new EndpointResolutionContext();
        return new CompositeEndpointResolver(java.util.List.of(
                new SpringResolver(module, resolutionContext),
                new JaxrsResolver(module, resolutionContext)
        ));
    }

    /**
     * 创建项目范围的 resolver 列表，并让它们共享本次解析的模块配置快照。
     */
    static @NotNull CompositeEndpointResolver forProject(@NotNull Project project) {
        EndpointResolutionContext resolutionContext = new EndpointResolutionContext();
        return new CompositeEndpointResolver(java.util.List.of(
                new SpringResolver(project, resolutionContext),
                new JaxrsResolver(project, resolutionContext)
        ));
    }
}
