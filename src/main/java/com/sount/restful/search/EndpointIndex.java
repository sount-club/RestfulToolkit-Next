package com.sount.restful.search;

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
import com.sount.restful.common.resolver.BaseServiceResolver;
import com.sount.restful.navigation.RestServiceItem;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class EndpointIndex implements Disposable {
    private static final Logger LOG = Logger.getInstance(EndpointIndex.class);

    private final Project myProject;
    private volatile List<RestServiceItem> myItems = Collections.emptyList();
    private final AtomicBoolean myDirty = new AtomicBoolean(true);
    private final AtomicBoolean myRebuilding = new AtomicBoolean(false);
    private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    private final List<Runnable> myListeners = new CopyOnWriteArrayList<>();

    private static final int DEBOUNCE_MS = 1000;
    private static final int RETRY_DELAY_MS = 2000;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private volatile int retryCount = 0;

    public EndpointIndex(@NotNull Project project) {
        myProject = project;
        registerPsiListener();
        registerRootsListener();
        registerIndexingListener();
        // Initial load
        scheduleRebuild();
    }

    public static EndpointIndex getInstance(@NotNull Project project) {
        return project.getService(EndpointIndex.class);
    }

    public @NotNull Project getProject() {
        return myProject;
    }

    public List<RestServiceItem> getItems() {
        if (myDirty.get() && myRebuilding.compareAndSet(false, true)) {
            LOG.info("Endpoint index is dirty, scheduling rebuild...");
            retryCount = 0;
            scheduleRebuild();
        }
        return myItems;
    }

    public boolean isReady() {
        return !DumbService.isDumb(myProject) && !myDirty.get() && !myRebuilding.get();
    }

    public void refresh() {
        myDirty.set(true);
        retryCount = 0;
        scheduleRebuild();
    }

    public void addListener(@NotNull Runnable listener) {
        myListeners.add(listener);
    }

    public void removeListener(@NotNull Runnable listener) {
        myListeners.remove(listener);
    }

    @Override
    public void dispose() {
        myAlarm.cancelAllRequests();
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
                myDirty.set(true);
                notifyListeners();
                debounceRebuild();
            }
        });
    }

    private void registerIndexingListener() {
        DumbService.getInstance(myProject).runWhenSmart(() -> {
            LOG.info("Smart mode entered, triggering endpoint index rebuild for full multi-module coverage...");
            myDirty.set(true);
            scheduleRebuild();
        });
    }

    private void onPsiChanged(@NotNull PsiTreeChangeEvent event) {
        PsiFile file = event.getFile();
        if (file == null || !file.isValid()) return;

        if (canAffectEndpointIndex(file)) {
            myDirty.set(true);
            debounceRebuild();
        }
    }

    static boolean canAffectEndpointIndex(@NotNull PsiFile file) {
        var virtualFile = file.getVirtualFile();
        if (virtualFile == null) return false;
        String ext = virtualFile.getExtension();
        return "java".equals(ext) || "kt".equals(ext);
    }

    private void debounceRebuild() {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(this::doRebuild, DEBOUNCE_MS);
    }

    private void scheduleRebuild() {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(this::doRebuild, 0);
    }

    private void doRebuild() {
        if (!myProject.isOpen() || myProject.isDisposed()) return;
        myRebuilding.set(true);

        LOG.info("Starting endpoint index rebuild... (attempt " + (retryCount + 1) + ")");
        long startTime = System.nanoTime();

        ReadAction.nonBlocking(() -> {
            try {
                return BaseServiceResolver.findAllEndpoints(myProject);
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Throwable e) {
                LOG.warn("Failed to rebuild endpoint index (attempt " + (retryCount + 1) + ")", e);
                return null;
            }
        })
        .inSmartMode(myProject)
        .expireWith(this)
        .finishOnUiThread(ModalityState.defaultModalityState(), items -> {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            if (items == null) {
                // Error case: items is null means exception was caught inside the task
                myRebuilding.set(false);
                if (retryCount < MAX_RETRY_ATTEMPTS) {
                    retryCount++;
                    LOG.info("Scheduling retry in " + RETRY_DELAY_MS + "ms...");
                    myAlarm.cancelAllRequests();
                    myAlarm.addRequest(this::doRebuild, RETRY_DELAY_MS);
                } else {
                    LOG.warn("Max retry attempts reached. Endpoint index may be incomplete.");
                    myDirty.set(false);
                    retryCount = 0;
                    notifyListeners();
                }
                return;
            }

            myItems = items;
            myDirty.set(false);
            myRebuilding.set(false);
            retryCount = 0;

            LOG.info("Endpoint index rebuild complete. Found " + items.size()
                    + " endpoints in " + elapsedMs + "ms.");
            notifyListeners();
        })
        .submit(AppExecutorUtil.getAppExecutorService());
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
