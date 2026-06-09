package com.sount.restful.common.resolver;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.GlobalSearchScope;
import com.sount.restful.annotations.PathMappingAnnotation;
import com.sount.restful.navigation.RestServiceItem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class SpringJavaEndpointResolver {
    private final SpringResolver owner;

    SpringJavaEndpointResolver(@NotNull SpringResolver owner) {
        this.owner = owner;
    }

    @NotNull
    List<RestServiceItem> collect(@NotNull PathMappingAnnotation controllerAnnotation,
                                  @NotNull Project project,
                                  @NotNull GlobalSearchScope globalSearchScope,
                                  @NotNull Set<PsiClass> processedJavaClasses) {
        List<RestServiceItem> items = new ArrayList<>();
        var psiAnnotations = SpringResolver.findAnnotationsByShortName(
                controllerAnnotation.getShortName(), project, globalSearchScope);
        SpringResolver.LOG.debug("Found " + psiAnnotations.size() + " @" + controllerAnnotation.getShortName() + " annotations");
        for (PsiAnnotation psiAnnotation : psiAnnotations) {
            if (!(psiAnnotation.getParent() instanceof PsiModifierList psiModifierList)) {
                continue;
            }
            PsiElement psiElement = psiModifierList.getParent();

            if (!(psiElement instanceof PsiClass psiClass)) {
                continue;
            }
            if (!processedJavaClasses.add(psiClass)) continue;

            items.addAll(owner.getServiceItemList(psiClass));
        }
        return items;
    }
}
