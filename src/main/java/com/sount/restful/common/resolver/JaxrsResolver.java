package com.sount.restful.common.resolver;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.sount.restful.annotations.JaxrsPathAnnotation;
import com.sount.restful.common.jaxrs.JaxrsAnnotationHelper;
import com.sount.restful.method.RequestPath;
import com.sount.restful.navigation.action.RestServiceItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class JaxrsResolver extends BaseServiceResolver {
    private static final Logger LOG = Logger.getInstance(JaxrsResolver.class);

    public JaxrsResolver(Module module) {
        myModule = module;
    }

    public JaxrsResolver(Project project) {
        myProject = project;
    }

    @Override
    public List<RestServiceItem> getRestServiceItemList(Project project, GlobalSearchScope globalSearchScope) {
        List<RestServiceItem> itemList = new ArrayList<>();

        Collection<PsiAnnotation> psiAnnotations = findAnnotationsByShortName(
                JaxrsPathAnnotation.PATH.getShortName(), project, globalSearchScope);
        LOG.debug("Found " + psiAnnotations.size() + " @Path annotations");

        for (PsiAnnotation psiAnnotation : psiAnnotations) {
            if (!(psiAnnotation.getParent() instanceof PsiModifierList psiModifierList)) {
                continue;
            }
            PsiElement psiElement = psiModifierList.getParent();

            if (!(psiElement instanceof PsiClass psiClass)) continue;

            String classUriPath = JaxrsAnnotationHelper.getClassUriPath(psiClass);

            for (PsiMethod psiMethod : getClassMethodsIncludingParents(psiClass)) {
                RequestPath[] methodUriPaths = JaxrsAnnotationHelper.getRequestPaths(psiMethod);
                if (methodUriPaths == null) {
                    continue;
                }
                for (RequestPath methodUriPath : methodUriPaths) {
                    RestServiceItem item = createRestServiceItem(psiMethod, classUriPath, methodUriPath);
                    itemList.add(item);
                }
            }

        }

        LOG.info("JaxrsResolver found " + itemList.size() + " endpoints");
        return itemList;
    }

    private static Collection<PsiAnnotation> findAnnotationsByShortName(
            String shortName, Project project, GlobalSearchScope scope) {
        try {
            // Use JavaAnnotationIndex.getAnnotations() (non-deprecated) instead of get()
            return JavaAnnotationIndex.getInstance().getAnnotations(shortName, project, scope);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Throwable e) {
            // Handle index inconsistency gracefully - log and return empty collection
            // This can happen when IDE index is corrupted, being rebuilt, or has stub/text mismatch
            LOG.warn("Failed to find @" + shortName + " annotations (index may be inconsistent)", e);
            return new ArrayList<>();
        }
    }
}
