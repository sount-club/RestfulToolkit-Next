package com.sount.restful.common.resolver;


import com.intellij.openapi.module.Module;
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

    public JaxrsResolver(Module module) {
        myModule = module;
    }

    public JaxrsResolver(Project project) {
        myProject = project;
    }

    /*
    protected List<RestServiceItem> getServiceItemList(PsiMethod psiMethod) {
        List<RestServiceItem> itemList = new ArrayList<>();

        String classUriPath = JaxrsAnnotationHelper.getClassUriPath(psiMethod.getContainingClass());

        RequestPath[] methodUriPaths = JaxrsAnnotationHelper.getRequestPaths(psiMethod);

        for (RequestPath methodUriPath : methodUriPaths) {
            RestServiceItem item = createRestServiceItem(psiMethod, classUriPath, methodUriPath);
            itemList.add(item);
        }

        return itemList;
    }*/

    /*@NotNull
    public List<PsiMethod> getServicePsiMethodList(Project project, GlobalSearchScope globalSearchScope) {
        List<PsiMethod> psiMethodList = new ArrayList<>();

        for (PathMappingAnnotation supportedAnnotation : JaxrsPathAnnotation.values()) {

// 标注了 jaxrs Path 注解的类
            Collection<PsiAnnotation> psiAnnotations = JavaAnnotationIndex.getInstance().get(supportedAnnotation.getShortName(), project, globalSearchScope);

            for (PsiAnnotation psiAnnotation : psiAnnotations) {
                PsiModifierList psiModifierList = (PsiModifierList) psiAnnotation.getParent();
                PsiElement psiElement = psiModifierList.getParent();
//                System.out.println("psiElement : "+ psiElement);

                if (!(psiElement instanceof PsiClass)) continue;

                PsiClass psiClass = (PsiClass) psiElement;
                PsiMethod[] psiMethods = psiClass.getMethods();

                if (psiMethods == null) {
                    continue;
                }

                psiMethodList.addAll(Arrays.asList(psiMethods));

            }
        }
        return psiMethodList;
    }*/


    @Override
    public List<RestServiceItem> getRestServiceItemList(Project project, GlobalSearchScope globalSearchScope) {
        List<RestServiceItem> itemList = new ArrayList<>();

        Collection<PsiAnnotation> psiAnnotations = findAnnotationsByShortName(
                JaxrsPathAnnotation.PATH.getShortName(), project, globalSearchScope);

        for (PsiAnnotation psiAnnotation : psiAnnotations) {
            PsiModifierList psiModifierList = (PsiModifierList) psiAnnotation.getParent();
            PsiElement psiElement = psiModifierList.getParent();

            if (!(psiElement instanceof PsiClass psiClass)) continue;

            PsiMethod[] psiMethods = psiClass.getMethods();

            if (psiMethods == null) {
                continue;
            }

            String classUriPath = JaxrsAnnotationHelper.getClassUriPath(psiClass);

            for (PsiMethod psiMethod : psiMethods) {
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

        return itemList;
    }

    private static Collection<PsiAnnotation> findAnnotationsByShortName(
            String shortName, Project project, GlobalSearchScope scope) {
        // Use JavaAnnotationIndex.getAnnotations() (non-deprecated) instead of get()
        return JavaAnnotationIndex.getInstance().getAnnotations(shortName, project, scope);
    }
}
