package com.sount.restful.endpoint.resolver;

import com.sount.restful.endpoint.navigation.RestServiceItem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 按顺序组合多个端点解析策略，并统一执行保序去重。
 */
final class CompositeEndpointResolver implements EndpointResolver {
    private final List<EndpointResolver> strategies;

    /**
     * 创建组合解析器并冻结策略顺序；顺序决定重复端点的保留优先级。
     */
    CompositeEndpointResolver(@NotNull List<EndpointResolver> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    /**
     * 依次执行所有模块范围策略，并按搜索选择键保序去重。
     */
    @Override
    public @NotNull List<RestServiceItem> resolveModuleEndpoints() {
        return resolveAndMerge(EndpointResolver::resolveModuleEndpoints);
    }

    /**
     * 依次执行所有项目范围策略，并按搜索选择键保序去重。
     */
    @Override
    public @NotNull List<RestServiceItem> resolveProjectEndpoints() {
        return resolveAndMerge(EndpointResolver::resolveProjectEndpoints);
    }

    /**
     * 返回不可变策略列表，仅用于同包架构测试验证装配顺序。
     */
    @NotNull List<EndpointResolver> strategies() {
        return strategies;
    }

    /**
     * 执行策略并按既有 selection key 规则合并结果，首个重复项优先保留。
     */
    private @NotNull List<RestServiceItem> resolveAndMerge(
            @NotNull Function<EndpointResolver, List<RestServiceItem>> operation) {
        Map<String, RestServiceItem> deduped = new LinkedHashMap<>();
        for (EndpointResolver strategy : strategies) {
            for (RestServiceItem item : operation.apply(strategy)) {
                deduped.putIfAbsent(item.getSearchSelectionKey(), item);
            }
        }
        return new ArrayList<>(deduped.values());
    }
}
