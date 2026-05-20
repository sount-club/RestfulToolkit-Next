package com.sount.restful.search;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
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
        // Initial load
        scheduleRebuild();
    }

    public static EndpointIndex getInstance(@NotNull Project project) {
        return project.getService(EndpointIndex.class);
    }

    public List<RestServiceItem> getItems() {
        if (myDirty.get() && myRebuilding.compareAndSet(false, true)) {
            scheduleRebuild();
        }
        return myItems;
    }

    public void refresh() {
        myDirty.set(true);
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
            List<RestServiceItem> items = ReadAction.nonBlocking(
                            () -> BaseServiceResolver.findAllEndpoints(myProject))
                    .inSmartMode(myProject)
                    .expireWith(myProject)
                    .submit(AppExecutorUtil.getAppExecutorService())
                    .get();

            myItems = items != null ? items : Collections.emptyList();
            myDirty.set(false);
            myRebuilding.set(false);

            notifyListeners();
        } catch (Exception e) {
            LOG.warn("Failed to rebuild endpoint index", e);
            myRebuilding.set(false);
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
