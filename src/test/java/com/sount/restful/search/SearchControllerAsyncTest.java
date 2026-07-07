package com.sount.restful.search;

import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import com.sount.restful.navigation.RestServiceItem;

import javax.swing.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Verifies the asynchronous search path (C3): scoring runs on a pooled thread and results
 * are applied on the EDT under {@link SearchController.UpdateGuard}, so large endpoint sets
 * do not block the event dispatch thread and stale results are discarded.
 */
public class SearchControllerAsyncTest extends BasePlatformTestCase {

    public void testPerformSearchPopulatesModelFromBackgroundThread() {
        RestServiceItem item = createItem("GET", "/api/users", "UserController", "users");
        EndpointIndex index = stubIndex(List.of(item));

        DefaultListModel<SearchResult> model = new DefaultListModel<>();
        JBList<SearchResult> resultList = new JBList<>(model);
        UnifiedSearchRenderer renderer = new UnifiedSearchRenderer();
        SearchController.UpdateGuard guard = new SearchController.UpdateGuard();

        SearchController.performSearch("users", index, model, resultList,
                new JLabel(), new JButton(), null, renderer, guard, null,
                null, null, null, null);

        // Scoring runs on a pooled thread; wait for the EDT to apply the results.
        waitUntil(() -> !model.isEmpty());

        assertEquals("Background scoring should populate the model asynchronously",
                1, model.getSize());
        assertEquals("/api/users", model.get(0).item().getUrl());
    }

    public void testStaleBackgroundResultsAreDiscardedByUpdateGuard() {
        RestServiceItem item = createItem("GET", "/api/users", "UserController", "users");
        EndpointIndex index = stubIndex(List.of(item));

        DefaultListModel<SearchResult> model = new DefaultListModel<>();
        JBList<SearchResult> resultList = new JBList<>(model);
        UnifiedSearchRenderer renderer = new UnifiedSearchRenderer();
        SearchController.UpdateGuard guard = new SearchController.UpdateGuard();

        // First search returns no results; second matches. Only the latest generation wins.
        SearchController.performSearch("nomatch", index, model, resultList,
                new JLabel(), new JButton(), null, renderer, guard, null, null, null, null, null);
        SearchController.performSearch("users", index, model, resultList,
                new JLabel(), new JButton(), null, renderer, guard, null, null, null, null, null);

        waitUntil(() -> model.getSize() == 1
                && "/api/users".equals(model.get(0).item().getUrl()));

        assertEquals(1, model.getSize());
        assertEquals("/api/users", model.get(0).item().getUrl());
    }

    /**
     * An EndpointIndex whose getItems() returns a fixed list, decoupling the test from
     * the async rebuild timing. Registered on the test root disposable for cleanup.
     */
    private EndpointIndex stubIndex(List<RestServiceItem> items) {
        EndpointIndex index = new EndpointIndex(getProject()) {
            @Override
            public List<RestServiceItem> getItems() {
                return items;
            }

            @Override
            public boolean isReady() {
                return true;
            }
        };
        Disposer.register(getTestRootDisposable(), index);
        return index;
    }

    private RestServiceItem createItem(String methodText, String url, String className, String methodName) {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText(className + ".java", """
                package demo;

                public class %s {
                    public void %s() {}
                }
                """.formatted(className, methodName));
        PsiClass psiClass = javaFile.getClasses()[0];
        PsiMethod method = psiClass.findMethodsByName(methodName, false)[0];
        return new RestServiceItem(method, methodText, url);
    }

    /** Polls up to 10s, pumping EDT invocation events, until {@code condition} is true. */
    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for asynchronous search result");
            }
            UIUtil.dispatchAllInvocationEvents();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
