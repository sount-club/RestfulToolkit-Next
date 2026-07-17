package com.sount.restful.search.domain;

import com.sount.restful.endpoint.model.EndpointDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 搜索领域使用的不可变端点文档。
 *
 * <p>文档只暴露评分所需的纯数据，并通过泛型 payload 携带应用层对象。领域层不会读取
 * payload，因此导航对象即使包含 PSI 或 Module，也不会把平台依赖传入搜索算法。</p>
 *
 * @param item 调用方关联的原始对象，搜索结果会原样返回
 * @param descriptor 端点的不可变描述快照
 * @param contextPath 索引阶段捕获的 context-path
 * @param <T> 原始对象类型
 */
public record SearchDocument<T>(
        @NotNull T item,
        @NotNull EndpointDescriptor descriptor,
        @NotNull String contextPath
) {
    /**
     * 校验文档边界，避免空 payload 或空快照进入评分热路径。
     */
    public SearchDocument {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(contextPath, "contextPath");
    }
}
