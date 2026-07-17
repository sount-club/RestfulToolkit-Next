package com.sount.restful.search.ui;

import com.sount.restful.endpoint.navigation.RestServiceItem;
import com.sount.restful.search.domain.SearchDocument;
import com.sount.restful.search.domain.SearchResult;

final class SearchResultTestFactory {
    private SearchResultTestFactory() {
    }

    /**
     * 将平台测试端点包装为与生产代码一致的领域搜索结果。
     */
    static SearchResult<RestServiceItem> result(RestServiceItem item, int score, String matchDimension) {
        SearchDocument<RestServiceItem> document = new SearchDocument<>(
                item, item.getDescriptor(), item.getContextPath());
        return new SearchResult<>(document, score, matchDimension);
    }
}
