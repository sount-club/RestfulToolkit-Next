package com.sount.restful.common.resolver;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class ServiceResolverRegistry {

    private ServiceResolverRegistry() {
    }

    public static ServiceResolver @NotNull [] forModule(@NotNull Module module) {
        return new ServiceResolver[]{
                new SpringResolver(module),
                new JaxrsResolver(module)
        };
    }

    public static ServiceResolver @NotNull [] forProject(@NotNull Project project) {
        return new ServiceResolver[]{
                new SpringResolver(project),
                new JaxrsResolver(project)
        };
    }
}
