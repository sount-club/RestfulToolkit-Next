package com.sount.restful.common;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.caches.KotlinShortNamesCache;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import java.util.Collection;
import java.util.Collections;

// 处理 实体自关联，第二层自关联字段
public class KtClassHelper {
    KtClass psiClass;

    private static int autoCorrelationCount = 0; //标记实体递归
    private int listIterateCount = 0; //标记List递归
    private Module myModule;

    protected KtClassHelper(@NotNull KtClass psiClass) {
        this.psiClass = psiClass;
    }

    @NotNull
    protected Project getProject() {
        return psiClass.getProject();
    }

    @Nullable
    public KtClassOrObject findOnePsiClassByClassName(String className, Project project) {
        String shortClassName = className.substring(className.lastIndexOf(".") + 1, className.length());

        PsiClass[] classesByName = KotlinShortNamesCache.getInstance(project).getClassesByName(shortClassName, GlobalSearchScope.allScope(project));

        Collection<KtClassOrObject> ktClassOrObjects = tryDetectPsiClassByShortClassName(project, shortClassName);
        if (ktClassOrObjects == null || ktClassOrObjects.size() == 0) {
            return null;
        }
        if (ktClassOrObjects.size() == 1) {
            return ktClassOrObjects.iterator().next();
        }
        // For multiple results, return the first one
        return ktClassOrObjects.iterator().next();
    }

    public Collection<KtClassOrObject> tryDetectPsiClassByShortClassName(Project project, String shortClassName) {
        // Try to get Kotlin classes by short name using KotlinShortNamesCache
        PsiClass[] classesByName = KotlinShortNamesCache.getInstance(project).getClassesByName(shortClassName, GlobalSearchScope.allScope(project));

        // Filter to get only KtClassOrObject instances
        java.util.List<KtClassOrObject> ktClassOrObjects = new java.util.ArrayList<>();
        for (PsiClass psiClass : classesByName) {
            if (psiClass instanceof KtClassOrObject) {
                ktClassOrObjects.add((KtClassOrObject) psiClass);
            }
        }

        return ktClassOrObjects;
    }

    public static KtClassHelper create(@NotNull KtClass psiClass) {
        return new KtClassHelper(psiClass);
    }
}
