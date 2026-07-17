package com.sount.restful.search.domain;

import com.sount.restful.endpoint.model.EndpointDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 纯领域端点搜索引擎，负责过滤、评分与稳定的 top-N 排序。
 */
public final class SearchEngine {

    private static final int SCORE_PATH_EXACT = 200;
    private static final int SCORE_PATH_STARTS_WITH = 130;
    private static final int SCORE_PATH_CONTAINS = 95;
    private static final int SCORE_METHOD_NAME_EXACT = 90;
    private static final int SCORE_METHOD_NAME_CONTAINS = 65;
    private static final int SCORE_DESCRIPTION_CONTAINS = 70;
    private static final int SCORE_HTTP_METHOD_EXACT = 75;
    private static final int SCORE_MODULE_NAME_CONTAINS = 55;
    private static final int SCORE_CONTROLLER_NAME_CONTAINS = 45;
    private static final int MAX_USE_COUNT_BONUS = 50;

    private SearchEngine() {}

    /**
     * 使用默认上限和默认路径配置搜索端点文档。
     */
    public static <T> @NotNull List<SearchResult<T>> search(
            @Nullable SearchQuery query,
            @NotNull List<SearchDocument<T>> documents) {
        return search(query, documents, 200, null);
    }

    /**
     * 使用指定结果上限和默认路径配置搜索端点文档。
     */
    public static <T> @NotNull List<SearchResult<T>> search(
            @Nullable SearchQuery query,
            @NotNull List<SearchDocument<T>> documents,
            int maxResults) {
        return search(query, documents, maxResults, null);
    }

    /**
     * 使用历史次数加权和默认路径配置搜索端点文档。
     */
    public static <T> @NotNull List<SearchResult<T>> search(
            @Nullable SearchQuery query,
            @NotNull List<SearchDocument<T>> documents,
            int maxResults,
            @Nullable Function<? super T, Integer> useCountLookup) {
        return search(query, documents, maxResults, useCountLookup, PathSearchOptions.EMPTY);
    }

    /**
     * 根据当前端点快照执行搜索，并返回按既有评分规则排序的前 N 个结果。
     *
     * <p><b>行为约束：</b>方法必须保持路径、方法名、描述、HTTP 方法、模块和 Controller
     * 的既有权重，以及 {@link SearchResult} 定义的稳定排序规则。</p>
     * <p><b>线程约束：</b>搜索过程中只读取端点的预计算字段，不访问 PSI 或 Swing，
     * 可安全地由后台搜索任务调用。</p>
     * <p><b>性能约束：</b>使用有界最小堆保存 top-N，禁止对全部命中结果执行完整排序。</p>
     *
     * @param query 搜索查询；为空或空查询时返回按默认顺序排列的端点
     * @param documents 当前不可变的搜索文档快照
     * @param maxResults 最大结果数
     * @param useCountLookup 端点使用次数查询器，可为空
     * @param pathSearchOptions 路径搜索配置
     * @return 不超过 {@code maxResults} 的有序搜索结果
     */
    public static <T> @NotNull List<SearchResult<T>> search(
            @Nullable SearchQuery query,
            @NotNull List<SearchDocument<T>> documents,
            int maxResults,
            @Nullable Function<? super T, Integer> useCountLookup,
            @NotNull PathSearchOptions pathSearchOptions) {
        if (query == null || query.isEmpty()) {
            return documents.stream()
                    .map(document -> new SearchResult<>(document, 0, null))
                    .sorted()
                    .limit(maxResults)
                    .collect(Collectors.toList());
        }

        List<String> lowerTokens = query.tokens().stream()
                .map(t -> t.toLowerCase(Locale.ROOT))
                .toList();

        // SearchResult 的自然顺序是“最佳优先”；堆需要反转，让最差结果位于堆顶以便淘汰。
        PriorityQueue<SearchResult<T>> topN = new PriorityQueue<>(maxResults + 1, Comparator.reverseOrder());
        for (SearchDocument<T> document : documents) {
            ScoreResult sr = scoreItem(query, document, lowerTokens, useCountLookup, pathSearchOptions);
            if (sr.score > 0) {
                topN.add(new SearchResult<>(document, sr.score, null, sr.matchedFields));
                if (topN.size() > maxResults) {
                    topN.poll(); // remove lowest score
                }
            }
        }

        List<SearchResult<T>> results = new ArrayList<>(topN);
        Collections.sort(results);

        return results;
    }

    /**
     * 对单个端点执行硬过滤、路径匹配和逐 token 评分。
     *
     * <p><b>行为约束：</b>HTTP 方法过滤属于硬过滤；绝对路径查询命中后直接返回路径分值；
     * 普通文本查询要求所有 token 都出现在端点的可搜索字段中。</p>
     * <p><b>性能约束：</b>先使用预拼接的 searchableText 快速排除不匹配端点，
     * 再读取各字段的预计算小写值完成精确评分。</p>
     */
    private static <T> @NotNull ScoreResult scoreItem(
            @NotNull SearchQuery query,
            @NotNull SearchDocument<T> document,
            @NotNull List<String> lowerTokens,
            @Nullable Function<? super T, Integer> useCountLookup,
            @NotNull PathSearchOptions pathSearchOptions) {
        EndpointDescriptor endpoint = document.descriptor();
        int score = 0;
        Set<String> matchedFields = null; // lazy init

        if (query.methodFilter() != null) {
            if (endpoint.method() != query.methodFilter()) {
                return ScoreResult.NO_MATCH;
            }
            score += SCORE_HTTP_METHOD_EXACT;
            matchedFields = addMatchedField(matchedFields, MatchField.HTTP_METHOD);
        }

        ScoreResult pathMatch = scorePathQuery(query, document, pathSearchOptions, score, matchedFields);
        if (pathMatch != null) {
            return addUseCountBonus(pathMatch, document.item(), useCountLookup);
        }

        if (lowerTokens.isEmpty()) {
            if (query.methodFilter() != null) {
                return new ScoreResult(score, matchedFields != null ? matchedFields : Collections.emptySet());
            }
            return ScoreResult.NO_MATCH;
        }

        if (!containsEveryToken(endpoint.searchableText(), lowerTokens)) {
            return ScoreResult.NO_MATCH;
        }

        for (String token : lowerTokens) {
            TokenMatch tokenMatch = scoreToken(endpoint, token);
            if (tokenMatch != TokenMatch.NO_MATCH) {
                score += tokenMatch.score;
                matchedFields = addMatchedField(matchedFields, tokenMatch.field);
            }
        }

        return addUseCountBonus(new ScoreResult(score,
                matchedFields != null ? matchedFields : Collections.emptySet()), document.item(), useCountLookup);
    }

    /**
     * 使用端点预拼接的搜索文本快速判断所有 token 是否均可命中。
     */
    private static boolean containsEveryToken(@NotNull String searchableText, @NotNull List<String> tokens) {
        for (String token : tokens) {
            if (!searchableText.contains(token)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算单个 token 在端点各搜索字段中的最高分，并保持字段优先级与旧实现一致。
     *
     * <p><b>兼容约束：</b>HTTP 方法仍只在完整相等时加分；方法子串 token 已经通过
     * searchableText 硬过滤时允许零分通过，以保持旧搜索结果集合不变。</p>
     * <p><b>性能约束：</b>只读取 descriptor 的预计算小写字段，返回枚举常量，
     * 避免在逐 token 热路径中创建临时结果对象。</p>
     */
    private static @NotNull TokenMatch scoreToken(@NotNull EndpointDescriptor endpoint, @NotNull String token) {
        TokenMatch best = TokenMatch.NO_MATCH;
        best = betterMatch(best, endpoint.lowerUrl().equals(token), TokenMatch.PATH_EXACT);
        best = betterMatch(best, endpoint.lowerUrl().startsWith(token), TokenMatch.PATH_STARTS_WITH);
        best = betterMatch(best, endpoint.lowerUrl().contains(token), TokenMatch.PATH_CONTAINS);
        best = betterMatch(best, endpoint.lowerMethodName().equals(token), TokenMatch.METHOD_NAME_EXACT);
        best = betterMatch(best, endpoint.lowerMethodName().contains(token), TokenMatch.METHOD_NAME_CONTAINS);
        best = betterMatch(best, endpoint.lowerDescription().contains(token), TokenMatch.DESCRIPTION_CONTAINS);
        best = betterMatch(best, endpoint.lowerMethodText().equals(token), TokenMatch.HTTP_METHOD_EXACT);
        best = betterMatch(best, endpoint.lowerModuleName().contains(token), TokenMatch.MODULE_NAME_CONTAINS);
        return betterMatch(best, endpoint.lowerControllerName().contains(token), TokenMatch.CONTROLLER_NAME_CONTAINS);
    }

    /**
     * 仅在候选字段真实命中且分值更高时替换当前结果，从而保持旧评分的字段优先级。
     */
    private static @NotNull TokenMatch betterMatch(@NotNull TokenMatch current,
                                                    boolean matches,
                                                    @NotNull TokenMatch candidate) {
        return matches && candidate.score > current.score ? candidate : current;
    }

    /**
     * 对单个绝对路径查询执行模板匹配与前缀候选评分。
     *
     * <p><b>行为约束：</b>普通文本查询返回 {@code null}，交由原有文本评分流程处理；
     * 未修改的请求路径始终优先于剥离 gateway/context-path 后的候选。</p>
     */
    private static @Nullable ScoreResult scorePathQuery(@NotNull SearchQuery query,
                                                        @NotNull SearchDocument<?> document,
                                                        @NotNull PathSearchOptions options, int methodScore,
                                                        @Nullable Set<String> matchedFields) {
        if (query.tokens().size() != 1 || !query.tokens().get(0).startsWith("/")) {
            return null;
        }
        String rawPath = query.tokens().get(0);
        String endpointPath = document.descriptor().url();
        if (endpointPath == null || endpointPath.isBlank()) {
            return null;
        }

        String normalizedEndpointPath = PathTemplateMatcher.normalizePath(endpointPath);
        List<String> candidates = PathTemplateMatcher.candidates(
                rawPath, document.contextPath(), options.gatewayPrefixes());
        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            int matchScore = scorePathCandidate(endpointPath, normalizedEndpointPath, candidate);
            if (matchScore > 0) {
                Set<String> fields = addMatchedField(matchedFields, MatchField.PATH);
                return new ScoreResult(methodScore + matchScore - i * 5, fields);
            }
        }
        return null;
    }

    /**
     * 计算一个真实路径候选与端点模板的基础匹配分，不包含候选优先级衰减。
     */
    private static int scorePathCandidate(@NotNull String endpointPath,
                                          @NotNull String normalizedEndpointPath,
                                          @NotNull String candidate) {
        if (PathTemplateMatcher.matches(endpointPath, candidate)) {
            return endpointPath.equalsIgnoreCase(candidate) ? SCORE_PATH_EXACT : SCORE_PATH_STARTS_WITH;
        }
        String normalizedCandidate = PathTemplateMatcher.normalizePath(candidate);
        return normalizedEndpointPath.startsWith(normalizedCandidate) ? SCORE_PATH_STARTS_WITH : 0;
    }

    private static <T> @NotNull ScoreResult addUseCountBonus(
            @NotNull ScoreResult result,
            @NotNull T item,
            @Nullable Function<? super T, Integer> useCountLookup) {
        if (result.score <= 0 || useCountLookup == null) {
            return result;
        }
        int useCount = useCountLookup.apply(item);
        return new ScoreResult(result.score + Math.min(useCount, MAX_USE_COUNT_BONUS), result.matchedFields);
    }

    private static Set<String> addMatchedField(@Nullable Set<String> set, String field) {
        if (set == null) {
            set = new LinkedHashSet<>();
        }
        set.add(field);
        return set;
    }

    private record ScoreResult(int score, Set<String> matchedFields) {
        static final ScoreResult NO_MATCH = new ScoreResult(0, Collections.emptySet());
    }

    private enum TokenMatch {
        NO_MATCH(0, null),
        PATH_EXACT(SCORE_PATH_EXACT, MatchField.PATH),
        PATH_STARTS_WITH(SCORE_PATH_STARTS_WITH, MatchField.PATH),
        PATH_CONTAINS(SCORE_PATH_CONTAINS, MatchField.PATH),
        METHOD_NAME_EXACT(SCORE_METHOD_NAME_EXACT, MatchField.METHOD_NAME),
        METHOD_NAME_CONTAINS(SCORE_METHOD_NAME_CONTAINS, MatchField.METHOD_NAME),
        DESCRIPTION_CONTAINS(SCORE_DESCRIPTION_CONTAINS, MatchField.DESCRIPTION),
        HTTP_METHOD_EXACT(SCORE_HTTP_METHOD_EXACT, MatchField.HTTP_METHOD),
        MODULE_NAME_CONTAINS(SCORE_MODULE_NAME_CONTAINS, MatchField.MODULE_NAME),
        CONTROLLER_NAME_CONTAINS(SCORE_CONTROLLER_NAME_CONTAINS, MatchField.CONTROLLER_NAME);

        private final int score;
        private final String field;

        TokenMatch(int score, String field) {
            this.score = score;
            this.field = field;
        }
    }
}
