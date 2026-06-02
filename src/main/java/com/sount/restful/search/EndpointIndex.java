package com.sount.restful.search;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
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
import com.sount.restful.navigation.action.RestServiceItem;
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

    private static final int DEBOUNCE_MS = 500;
    private static final int RETRY_DELAY_MS = 2000;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private volatile int retryCount = 0;

    private static final String[] CONTROLLER_ANNOTATIONS = {
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "javax.ws.rs.Path",
            "jakarta.ws.rs.Path"
    };

    private static final String[] MAPPING_ANNOTATIONS = {
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping"
    };

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
        myProject.getMessageBus().connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
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

        // Only invalidate for files that likely contain REST annotations
        if (containsRestAnnotations(file)) {
            myDirty.set(true);
            debounceRebuild();
        }
    }

    private boolean containsRestAnnotations(@NotNull PsiFile file) {
        String text = file.getText();
        // Quick text check before expensive PSI traversal
        for (String annotation : CONTROLLER_ANNOTATIONS) {
            String simpleName = annotation.substring(annotation.lastIndexOf('.') + 1);
            if (text.contains(simpleName)) return true;
        }
        for (String annotation : MAPPING_ANNOTATIONS) {
            String simpleName = annotation.substring(annotation.lastIndexOf('.') + 1);
            if (text.contains(simpleName)) return true;
        }
        return false;
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

        try {
            LOG.info("Starting endpoint index rebuild... (attempt " + (retryCount + 1) + ")");
            List<RestServiceItem> items = ReadAction.nonBlocking(
                            () -> BaseServiceResolver.findAllEndpoints(myProject))
                    .inSmartMode(myProject)
                    .expireWith(myProject)
                    .submit(AppExecutorUtil.getAppExecutorService())
                    .get();

            myItems = items != null ? items : Collections.emptyList();
            myDirty.set(false);
            myRebuilding.set(false);
            retryCount = 0;

            LOG.info("Endpoint index rebuild complete. Found " + myItems.size() + " endpoints.");
            notifyListeners();
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Throwable e) {
            // Catch all errors including index inconsistency errors from IDE
            // These are temporary issues that will resolve after IDE reindexes
            LOG.warn("Failed to rebuild endpoint index (attempt " + (retryCount + 1) + ")", e);
            myRebuilding.set(false);

            // Schedule retry if we haven't exceeded max attempts
            if (retryCount < MAX_RETRY_ATTEMPTS) {
                retryCount++;
                LOG.info("Scheduling retry in " + RETRY_DELAY_MS + "ms...");
                myAlarm.cancelAllRequests();
                myAlarm.addRequest(this::doRebuild, RETRY_DELAY_MS);
            } else {
                LOG.warn("Max retry attempts reached. Endpoint index may be incomplete.");
                myDirty.set(false);
                retryCount = 0;
                // Notify listeners even on failure so UI can update
                notifyListeners();
            }
        }
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
