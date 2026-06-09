package com.sount.restful.common.resolver;


import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.sount.restful.method.RequestPath;
import com.sount.restful.navigation.action.RestServiceItem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseServiceResolver implements ServiceResolver{
    private static final com.intellij.openapi.diagnostic.Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance(BaseServiceResolver.class);

    Module myModule;
    Project myProject;

    @NotNull
    public static List<RestServiceItem> findAllEndpoints(@NotNull Module module) {
        Map<String, RestServiceItem> deduped = new LinkedHashMap<>();
        for (ServiceResolver resolver : new ServiceResolver[]{new SpringResolver(module), new JaxrsResolver(module)}) {
            for (RestServiceItem item : resolver.findAllSupportedServiceItemsInModule()) {
                deduped.putIfAbsent(item.getSearchSelectionKey(), item);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    @NotNull
    public static List<RestServiceItem> findAllEndpoints(@NotNull Project project) {
        Map<String, RestServiceItem> deduped = new LinkedHashMap<>();
        for (ServiceResolver resolver : new ServiceResolver[]{new SpringResolver(project), new JaxrsResolver(project)}) {
            for (RestServiceItem item : resolver.findAllSupportedServiceItemsInProject()) {
                deduped.putIfAbsent(item.getSearchSelectionKey(), item);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    @Override
    public List<RestServiceItem> findAllSupportedServiceItemsInModule() {
        List<RestServiceItem> itemList = new ArrayList<>();
        if (myModule == null) {
            return itemList;
        }

        GlobalSearchScope globalSearchScope = GlobalSearchScope.moduleScope(myModule);

        itemList = getRestServiceItemList(myModule.getProject(), globalSearchScope);
        return itemList;
    }


    public abstract List<RestServiceItem> getRestServiceItemList(Project project, GlobalSearchScope globalSearchScope) ;

    @NotNull
    protected List<PsiMethod> getClassMethodsIncludingParents(@NotNull PsiClass psiClass) {
        List<PsiMethod> allMethods = new ArrayList<>();
        PsiClass currentClass = psiClass;
        while (currentClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(currentClass.getQualifiedName())) {
            Collections.addAll(allMethods, currentClass.getMethods());
            currentClass = currentClass.getSuperClass();
        }
        return allMethods;
    }

    @Override
    public List<RestServiceItem> findAllSupportedServiceItemsInProject() {
        List<RestServiceItem> itemList = null;
        if(myProject == null && myModule != null){
            myProject = myModule.getProject();
        }

        if (myProject == null) {
            return new ArrayList<>();
        }

        GlobalSearchScope globalSearchScope = GlobalSearchScope.projectScope(myProject);

        try {
            itemList = getRestServiceItemList(myProject, globalSearchScope);
            if (itemList == null) {
                itemList = new ArrayList<>();
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Throwable e) {
            // Handle any index inconsistency errors gracefully — return empty list.
            // Log at debug level since this is a transient condition during indexing,
            // and the platform may have already logged the error internally.
            LOG.debug("Failed to resolve REST endpoints (index may be inconsistent)", e);
            itemList = new ArrayList<>();
        }

        return itemList;

    }

    @NotNull
    protected RestServiceItem createRestServiceItem(PsiElement psiMethod, String classUriPath, RequestPath requestMapping) {
        if (!classUriPath.startsWith("/")) classUriPath = "/".concat(classUriPath);
        if (!classUriPath.endsWith("/")) classUriPath = classUriPath.concat("/");

        String methodPath = requestMapping.getPath();

        if (methodPath.startsWith("/")) methodPath = methodPath.substring(1, methodPath.length());
        String requestPath = classUriPath + methodPath;

        RestServiceItem item = new RestServiceItem(psiMethod, requestMapping.getMethod(), requestPath);
        if (myModule != null) {
            item.setModule(myModule);
        } else {
            Module module = ModuleUtil.findModuleForPsiElement(psiMethod);
            if (module != null) {
                item.setModule(module);
            }
        }
        return item;
    }
}
