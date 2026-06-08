package com.sount.restful.search;

import com.sount.restful.navigation.action.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public record SearchResult(
        @NotNull RestServiceItem item,
        int score,
        @Nullable String matchDimension,
        @NotNull Set<String> matchedFields
) implements Comparable<SearchResult> {

    /**
     * Backward-compatible constructor without match fields.
     */
    public SearchResult(@NotNull RestServiceItem item, int score, @Nullable String matchDimension) {
        this(item, score, matchDimension, Collections.emptySet());
    }

    @Override
    public int compareTo(@NotNull SearchResult other) {
        // Primary: score descending
        int scoreCmp = Integer.compare(other.score, this.score);
        if (scoreCmp != 0) return scoreCmp;

        // Secondary: URL alphabetical
        String thisUrl = this.item.getUrl();
        String otherUrl = other.item.getUrl();
        if (thisUrl != null && otherUrl != null) {
            return thisUrl.compareTo(otherUrl);
        }
        return 0;
    }
}
