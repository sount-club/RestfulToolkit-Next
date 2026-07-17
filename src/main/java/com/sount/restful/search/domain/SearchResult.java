package com.sount.restful.search.domain;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * 一次领域搜索产生的不可变结果。
 *
 * @param document 命中的搜索文档
 * @param score 综合评分
 * @param matchDimension 兼容保留的最佳匹配维度
 * @param matchedFields 所有参与评分的字段
 * @param <T> 调用方关联对象类型
 */
public record SearchResult<T>(
        @NotNull SearchDocument<T> document,
        int score,
        @Nullable String matchDimension,
        @NotNull Set<String> matchedFields
) implements Comparable<SearchResult<T>> {

    /**
     * 防御性复制匹配字段，保证后台线程创建的结果不会被 EDT 或调用方修改。
     */
    public SearchResult {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(matchedFields, "matchedFields");
        matchedFields = Set.copyOf(matchedFields);
    }

    /**
     * 创建不包含字段明细的兼容结果。
     */
    public SearchResult(@NotNull SearchDocument<T> document, int score, @Nullable String matchDimension) {
        this(document, score, matchDimension, Collections.emptySet());
    }

    /**
     * 返回应用层放入搜索文档的原始对象，供导航和展示继续使用。
     */
    public @NotNull T item() {
        return document.item();
    }

    @Override
    public int compareTo(@NotNull SearchResult<T> other) {
        // Primary: score descending
        int scoreCmp = Integer.compare(other.score, this.score);
        if (scoreCmp != 0) return scoreCmp;

        // Secondary: URL alphabetical
        String thisUrl = this.document.descriptor().url();
        String otherUrl = other.document.descriptor().url();
        if (thisUrl != null && otherUrl != null) {
            return thisUrl.compareTo(otherUrl);
        }
        return 0;
    }
}
