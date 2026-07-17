package com.sount.restful.search.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.sount.restful.endpoint.resolver.EndpointResolvers;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EndpointIndex implements Disposable {
    private static final Logger LOG = Logger.getInstance(EndpointIndex.class);

    private final Project myProject;
    private volatile List<RestServiceItem> myItems = Collections.emptyList();
    private final AtomicBoolean myDirty = new AtomicBoolean(true);
    private final AtomicBoolean myRebuilding = new AtomicBoolean(false);
    private final AtomicBoolean myRebuildScheduled = new AtomicBoolean(false);
    private final AtomicBoolean myDisposed = new AtomicBoolean(false);
    private final AtomicLong myDirtyGeneration = new AtomicLong();
    private final Object myStateLock = new Object();
    private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    private final List<Runnable> myListeners = new CopyOnWriteArrayList<>();

    private static final int DEBOUNCE_MS = 1000;
    private static final int PROJECT_OPEN_RETRY_DELAY_MS = 500;
    private static final int RETRY_DELAY_MS = 2000;
    private static final int EXTENDED_RETRY_DELAY_MS = 30000;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final AtomicReference<RebuildTask> myRebuildTask = new AtomicReference<>();

    public EndpointIndex(@NotNull Project project) {
        this(project, true);
    }

    EndpointIndex(@NotNull Project project, boolean initialize) {
        myProject = project;
        if (initialize) {
            registerPsiListener();
            registerRootsListener();
            registerIndexingListener();
            ensureRebuildScheduled();
        }
    }

    public static EndpointIndex getInstance(@NotNull Project project) {
        return project.getService(EndpointIndex.class);
    }

    public @NotNull Project getProject() {
        return myProject;
    }

    public List<RestServiceItem> getItems() {
        // Read-only snapshot getter. Rebuilds are driven by the PSI/roots/dumb-mode
        // listeners and the initial schedule in the constructor; a dirty flag by itself
        // never schedules a rebuild from a getter (avoids check-then-act races and
        // unrelated EDT work triggered from a read path).
        return myItems;
    }

    public boolean isReady() {
        return !DumbService.isDumb(myProject) && !myDirty.get() && !myRebuilding.get();
    }

    /**
     * Ensures a dirty endpoint index has an active or pending rebuild. UI entry points call
     * this explicitly so a previously cancelled startup task can recover without adding
     * side effects to {@link #getItems()}.
     */
    public void ensureRebuildScheduled() {
        if (myDisposed.get() || myProject.isDisposed() || !myDirty.get() || myRebuilding.get()) {
            return;
        }
        retryCount.set(0);
        rescheduleRebuild(0);
    }

    public void refresh() {
        markDirtyForRebuild();
        retryCount.set(0);
        rescheduleRebuild(0);
    }

    public void addListener(@NotNull Runnable listener) {
        myListeners.add(listener);
    }

    public void removeListener(@NotNull Runnable listener) {
        myListeners.remove(listener);
    }

    @Override
    public void dispose() {
        myDisposed.set(true);
        RebuildTask rebuildTask = myRebuildTask.getAndSet(null);
        if (rebuildTask != null && rebuildTask.promise != null) {
            rebuildTask.promise.cancel();
        }
        myAlarm.cancelAllRequests();
        myRebuildScheduled.set(false);
    }

    private void registerPsiListener() {
        PsiManager.getInstance(myProject).addPsiTreeChangeListener(new PsiTreeChangeListener() {
            @Override
            public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
            }

            @Override
            public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
            }

            @Override
            public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
            }

            @Override
            public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
            }

            @Override
            public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
            }

            @Override
            public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
            }

            @Override
            public void childAdded(@NotNull PsiTreeChangeEvent event) {
                onPsiChanged(event);
            }

            @Override
            public void childRemoved(@NotNull PsiTreeChangeEvent event) {
                onPsiChanged(event);
            }

            @Override
            public void childReplaced(@NotNull PsiTreeChangeEvent event) {
                onPsiChanged(event);
            }

            @Override
            public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
                onPsiChanged(event);
            }

            @Override
            public void childMoved(@NotNull PsiTreeChangeEvent event) {
                onPsiChanged(event);
            }

            @Override
            public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
            }
        }, this);
    }

    private void registerRootsListener() {
        myProject.getMessageBus().connect(this).subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
            @Override
            public void rootsChanged(@NotNull ModuleRootEvent event) {
                LOG.info("Project roots changed, scheduling endpoint index rebuild...");
                markDirtyForRebuild();
                notifyListeners();
                rescheduleRebuild(DEBOUNCE_MS);
            }
        });
    }

    private void registerIndexingListener() {
        DumbService.getInstance(myProject).runWhenSmart(() -> {
            LOG.info("Smart mode entered, triggering endpoint index rebuild for full multi-module coverage...");
            markDirtyForRebuild();
            rescheduleRebuild(0);
        });
    }

    private void onPsiChanged(@NotNull PsiTreeChangeEvent event) {
        PsiFile file = event.getFile();
        if (file == null || !file.isValid()) return;

        if (canAffectEndpointIndex(file)) {
            markDirtyForRebuild();
            rescheduleRebuild(DEBOUNCE_MS);
        }
    }

    static boolean canAffectEndpointIndex(@NotNull PsiFile file) {
        var virtualFile = file.getVirtualFile();
        if (virtualFile == null) return false;
        String ext = virtualFile.getExtension();
        return "java".equals(ext) || "kt".equals(ext);
    }

    private void rescheduleRebuild(int delayMillis) {
        myAlarm.cancelAllRequests();
        myRebuildScheduled.set(false);
        scheduleRebuild(delayMillis);
    }

    private void scheduleRebuild(int delayMillis) {
        if (myDisposed.get() || myProject.isDisposed()
                || !myRebuildScheduled.compareAndSet(false, true)) {
            return;
        }
        myAlarm.addRequest(() -> {
            myRebuildScheduled.set(false);
            doRebuild();
        }, delayMillis);
    }

    private void doRebuild() {
        if (myDisposed.get() || myProject.isDisposed()) return;
        if (!myProject.isOpen()) {
            scheduleRebuild(projectOpenRetryDelayMillis());
            return;
        }
        if (!myDirty.get()) return;
        if (!myRebuilding.compareAndSet(false, true)) {
            return;
        }
        long generation;
        synchronized (myStateLock) {
            generation = currentDirtyGeneration();
        }

        LOG.info("Starting endpoint index rebuild... (attempt " + (retryCount.get() + 1) + ")");
        long startTime = System.nanoTime();

        RebuildTask rebuildTask = new RebuildTask();
        myRebuildTask.set(rebuildTask);
        CancellablePromise<RebuildResult> promise = ReadAction.nonBlocking(() -> {
            try {
                return RebuildResult.success(resolveEndpoints());
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Throwable error) {
                return RebuildResult.failure(error);
            }
        })
        .inSmartMode(myProject)
        .expireWith(this)
        .finishOnUiThread(ModalityState.defaultModalityState(), result -> {
            if (!myRebuildTask.compareAndSet(rebuildTask, null)) {
                return;
            }
            if (result.error() != null) {
                handleRebuildFailure(generation, result.error());
            } else {
                handleRebuildSuccess(generation, startTime, result.items());
            }
        })
        .submit(AppExecutorUtil.getAppExecutorService());
        rebuildTask.promise = promise;
        if (myDisposed.get()) {
            promise.cancel();
            return;
        }
        promise.onError(error -> {
            if (myRebuildTask.compareAndSet(rebuildTask, null)) {
                handleRebuildFailure(generation, error);
            }
        });
    }

    protected @NotNull List<RestServiceItem> resolveEndpoints() {
        return EndpointResolvers.resolve(myProject);
    }

    private void handleRebuildSuccess(long generation, long startTime,
                                      @NotNull List<RestServiceItem> items) {
        synchronized (myStateLock) {
            if (isStaleRebuild(generation)) {
                myRebuilding.set(false);
                LOG.debug("Endpoint index rebuild result is stale; scheduling a fresh rebuild");
                scheduleRebuild(0);
                return;
            }
            myItems = List.copyOf(items);
            myDirty.set(false);
        }

        if (myDisposed.get() || myProject.isDisposed()) {
            myRebuilding.set(false);
            return;
        }

        myRebuilding.set(false);
        retryCount.set(0);
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        LOG.info("Endpoint index rebuild complete. Found " + items.size()
                + " endpoints in " + elapsedMs + "ms.");
        notifyListeners();
    }

    private void handleRebuildFailure(long generation, @NotNull Throwable error) {
        myRebuilding.set(false);
        myDirty.set(true);

        if (myDisposed.get() || myProject.isDisposed()) {
            return;
        }
        if (isStaleRebuild(generation)) {
            retryCount.set(0);
            scheduleRebuild(0);
            return;
        }

        int attempt = retryCount.incrementAndGet();
        if (!(error instanceof ProcessCanceledException)) {
            LOG.warn("Failed to rebuild endpoint index (attempt " + attempt + ")", error);
        } else {
            LOG.debug("Endpoint index rebuild was cancelled; scheduling recovery", error);
        }

        int delay = attempt <= maxRetryAttempts()
                ? retryDelayMillis()
                : extendedRetryDelayMillis();
        if (attempt > maxRetryAttempts()) {
            LOG.warn("Endpoint index rebuild is still failing; keeping the last snapshot and retrying later.");
            retryCount.set(0);
        }
        notifyListeners();
        scheduleRebuild(delay);
    }

    int projectOpenRetryDelayMillis() {
        return PROJECT_OPEN_RETRY_DELAY_MS;
    }

    int retryDelayMillis() {
        return RETRY_DELAY_MS;
    }

    int extendedRetryDelayMillis() {
        return EXTENDED_RETRY_DELAY_MS;
    }

    int maxRetryAttempts() {
        return MAX_RETRY_ATTEMPTS;
    }

    boolean isDirtyForTest() {
        return myDirty.get();
    }

    boolean isRebuildingForTest() {
        return myRebuilding.get();
    }

    private record RebuildResult(@NotNull List<RestServiceItem> items, Throwable error) {
        private static @NotNull RebuildResult success(@NotNull List<RestServiceItem> items) {
            return new RebuildResult(items, null);
        }

        private static @NotNull RebuildResult failure(@NotNull Throwable error) {
            return new RebuildResult(Collections.emptyList(), error);
        }
    }

    void markDirtyForRebuild() {
        synchronized (myStateLock) {
            myDirty.set(true);
            myDirtyGeneration.incrementAndGet();
        }
    }

    long currentDirtyGeneration() {
        return myDirtyGeneration.get();
    }

    boolean isStaleRebuild(long generation) {
        return generation != currentDirtyGeneration();
    }

    private static final class RebuildTask {
        private volatile CancellablePromise<RebuildResult> promise;
    }

    private void notifyListeners() {
        for (Runnable listener : myListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                LOG.warn("EndpointIndex listener failed", e);
            }
        }
    }
}
