package com.sount.restful.search.application;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;
import com.sount.restful.endpoint.navigation.RestServiceItem;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public class EndpointIndexTest extends BasePlatformTestCase {

    public void testJavaSourceFileWithoutRestAnnotationsCanInvalidateEndpointIndex() {
        PsiFile file = myFixture.configureByText("BaseController.java", """
                package demo;

                abstract class BaseController {
                    public String getById() { return null; }
                }
                """);

        assertTrue(EndpointIndex.canAffectEndpointIndex(file));
    }

    public void testDirtyGenerationMarksOlderRebuildAsStale() {
        EndpointIndex index = EndpointIndex.getInstance(getProject());

        long firstGeneration = index.currentDirtyGeneration();
        index.markDirtyForRebuild();
        long secondGeneration = index.currentDirtyGeneration();

        assertTrue(secondGeneration > firstGeneration);
        assertTrue(index.isStaleRebuild(firstGeneration));
        assertFalse(index.isStaleRebuild(secondGeneration));
    }

    public void testCancelledRebuildReleasesStateAndRetries() {
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("RecoveryController.java", """
                package demo;
                class RecoveryController {
                    void endpoint() {}
                }
                """);
        PsiClass psiClass = file.getClasses()[0];
        PsiMethod method = psiClass.findMethodsByName("endpoint", false)[0];
        RestServiceItem recoveredItem = new RestServiceItem(method, "GET", "/recovered");
        AtomicInteger attempts = new AtomicInteger();

        EndpointIndex index = new EndpointIndex(getProject(), false) {
            @Override
            protected List<RestServiceItem> resolveEndpoints() {
                if (attempts.incrementAndGet() == 1) {
                    throw new ProcessCanceledException();
                }
                return List.of(recoveredItem);
            }

            @Override
            int retryDelayMillis() {
                return 0;
            }
        };
        Disposer.register(getTestRootDisposable(), index);

        index.ensureRebuildScheduled();
        waitUntil(() -> attempts.get() >= 2 && index.isReady());

        assertFalse(index.isDirtyForTest());
        assertFalse(index.isRebuildingForTest());
        assertEquals(List.of(recoveredItem), index.getItems());
    }

    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for endpoint index recovery");
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
