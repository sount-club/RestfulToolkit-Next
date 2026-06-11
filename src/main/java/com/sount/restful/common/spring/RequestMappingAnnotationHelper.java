package com.sount.restful.common.spring;


import com.intellij.psi.*;
import com.sount.restful.annotations.SpringRequestMethodAnnotation;
import com.sount.restful.common.PsiAnnotationHelper;
import com.sount.restful.common.RestSupportedAnnotationHelper;
import com.sount.restful.method.RequestPath;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RequestMappingAnnotationHelper implements RestSupportedAnnotationHelper {

    private RequestMappingAnnotationHelper() {
    }

    public static List<RequestPath> getRequestPaths(PsiClass psiClass) {
        List<RequestPath> requestPaths = collectClassRequestPaths(psiClass, new HashSet<>());
        if (requestPaths.isEmpty()) {
            requestPaths.add(new RequestPath("/", null));
        }
        return requestPaths;
    }

    static boolean isSupportedRequestMappingAnnotation(PsiAnnotation annotation) {
        String qualifiedName = PsiAnnotationHelper.getQualifiedName(annotation);
        if (qualifiedName == null) {
            return false;
        }
        for (SpringRequestMethodAnnotation mappingAnnotation : SpringRequestMethodAnnotation.values()) {
            if (qualifiedName.equals(mappingAnnotation.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private static List<RequestPath> getRequestMappings(PsiAnnotation annotation, String defaultValue) {
        SpringRequestMethodAnnotation requestAnnotation = SpringRequestMethodAnnotation.getByQualifiedName(
                PsiAnnotationHelper.getQualifiedName(annotation));

        if (requestAnnotation == null) {
            return new ArrayList<>();
        }

        List<String> methodList;
        if (requestAnnotation.methodName() != null) {
            methodList = List.of(requestAnnotation.methodName());
        } else { // RequestMapping 如果没有指定具体method，不写的话，默认支持所有HTTP请求方法
            methodList = PsiAnnotationHelper.getAnnotationAttributeValues(annotation, "method");
        }

        List<String> pathList = getPathAttributeValues(annotation);

        if (pathList.isEmpty()) {
            pathList = List.of(defaultValue);
        }

        return buildRequestPaths(methodList, pathList);
    }

    private static List<RequestPath> buildRequestPaths(List<String> methodList, List<String> pathList) {
        List<RequestPath> mappingList = new ArrayList<>();
        if (!methodList.isEmpty()) {
            for (String method : methodList) {
                for (String path : pathList) {
                    mappingList.add(new RequestPath(path, method));
                }
            }
        } else {
            for (String path : pathList) {
                mappingList.add(new RequestPath(path, null));
            }
        }
        return mappingList;
    }

    public static RequestPath[] getRequestPaths(PsiMethod psiMethod) {
        return collectMethodRequestPaths(psiMethod, new HashSet<>()).toArray(new RequestPath[0]);
    }

    private static List<RequestPath> collectClassRequestPaths(PsiClass psiClass, Set<PsiClass> visitedClasses) {
        if (psiClass == null || !visitedClasses.add(psiClass)) {
            return new ArrayList<>();
        }

        List<RequestPath> ownRequestPaths = getRequestPathsFromModifierList(psiClass.getModifierList(), "");
        if (!ownRequestPaths.isEmpty()) {
            return ownRequestPaths;
        }

        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
            List<RequestPath> superClassRequestPaths = collectClassRequestPaths(superClass, visitedClasses);
            if (!superClassRequestPaths.isEmpty()) {
                return superClassRequestPaths;
            }
        }

        List<RequestPath> interfaceRequestPaths = new ArrayList<>();
        for (PsiClass psiInterface : psiClass.getInterfaces()) {
            interfaceRequestPaths.addAll(collectClassRequestPaths(psiInterface, visitedClasses));
        }
        return dedupe(interfaceRequestPaths);
    }

    private static List<RequestPath> collectMethodRequestPaths(PsiMethod psiMethod, Set<PsiMethod> visitedMethods) {
        if (psiMethod == null || !visitedMethods.add(psiMethod)) {
            return new ArrayList<>();
        }

        List<RequestPath> ownRequestPaths = getRequestPathsFromModifierList(psiMethod.getModifierList(), "/");
        if (!ownRequestPaths.isEmpty()) {
            return ownRequestPaths;
        }

        List<RequestPath> superMethodRequestPaths = new ArrayList<>();
        for (PsiMethod superMethod : psiMethod.findSuperMethods()) {
            superMethodRequestPaths.addAll(collectMethodRequestPaths(superMethod, visitedMethods));
        }
        return dedupe(superMethodRequestPaths);
    }

    private static List<RequestPath> getRequestPathsFromModifierList(@Nullable PsiModifierList modifierList,
                                                                     String defaultValue) {
        if (modifierList == null) {
            return new ArrayList<>();
        }

        List<RequestPath> requestPaths = new ArrayList<>();
        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            if (isSupportedRequestMappingAnnotation(annotation)) {
                requestPaths.addAll(getRequestMappings(annotation, defaultValue));
            }
        }
        return dedupe(requestPaths);
    }

    private static List<String> getPathAttributeValues(PsiAnnotation annotation) {
        List<String> pathList = PsiAnnotationHelper.getAnnotationAttributeValues(annotation, "value");
        if (pathList.isEmpty()) {
            pathList = PsiAnnotationHelper.getAnnotationAttributeValues(annotation, "path");
        }
        return pathList;
    }

    private static List<RequestPath> dedupe(List<RequestPath> requestPaths) {
        Map<String, RequestPath> deduped = new LinkedHashMap<>();
        for (RequestPath requestPath : requestPaths) {
            deduped.putIfAbsent(requestPath.getPath() + "#" + requestPath.getMethod(), requestPath);
        }
        return new ArrayList<>(deduped.values());
    }

    public static String[] getRequestMappingValues(PsiAnnotation annotation) {
        List<String> values = getPathAttributeValues(annotation);
        return values.toArray(new String[0]);
    }


    public static String getOneRequestMappingPath(PsiClass psiClass) {
        List<RequestPath> requestPaths = collectClassRequestPaths(psiClass, new HashSet<>());
        return requestPaths.isEmpty() ? "" : requestPaths.get(0).getPath();
    }


    public static String getOneRequestMappingPath(PsiMethod psiMethod) {
        RequestPath[] requestPaths = getRequestPaths(psiMethod);
        if (requestPaths.length > 0) {
            return requestPaths[0].getPath();
        }

        return StringUtils.uncapitalize(psiMethod.getName());
    }


}
