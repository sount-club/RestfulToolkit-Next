package com.sount.restful.common.spring;


import com.intellij.psi.*;
import com.sount.restful.annotations.SpringRequestMethodAnnotation;
import com.sount.restful.common.PsiAnnotationHelper;
import com.sount.restful.common.RestSupportedAnnotationHelper;
import com.sount.restful.method.RequestPath;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RequestMappingAnnotationHelper implements RestSupportedAnnotationHelper {

    /**
     * 过滤所有注解
     */
    public static List<RequestPath> getRequestPaths(PsiClass psiClass) {
        List<RequestPath> list = new ArrayList<>();
        if (psiClass.getModifierList() == null) {
            return list;
        }

        PsiAnnotation[] annotations = psiClass.getModifierList().getAnnotations();

        PsiAnnotation requestMappingAnnotation = getPsiAnnotation(annotations);

        if (requestMappingAnnotation != null) {
            List<RequestPath> requestMappings = getRequestMappings(requestMappingAnnotation, "");
            if (!requestMappings.isEmpty()) {
                list.addAll(requestMappings);
            }
        } else {
            // TODO : 继承 RequestMapping
            PsiClass superClass = psiClass.getSuperClass();
            if (superClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
                list = getRequestPaths(superClass);
            } else {
                list.add(new RequestPath("/", null));
            }

        }

        return list;
    }

    private static @Nullable PsiAnnotation getPsiAnnotation(PsiAnnotation[] annotations) {
        PsiAnnotation requestMappingAnnotation = null;
        for (PsiAnnotation annotation : annotations) {
            if (isSupportedRequestMappingAnnotation(annotation)) {
                requestMappingAnnotation = annotation;
            }
        }
        return requestMappingAnnotation;
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
        List<RequestPath> mappingList = new ArrayList<>();

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

        List<String> pathList = PsiAnnotationHelper.getAnnotationAttributeValues(annotation, "value");
        if (pathList.isEmpty()) {
            pathList = PsiAnnotationHelper.getAnnotationAttributeValues(annotation, "path");
        }

        // 没有设置 value，默认方法名
        if (pathList.isEmpty()) {
            pathList.add(defaultValue);
        }

        // todo: 处理没有设置 value 或 path 的 RequestMapping

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

    /**
     * 过滤所有注解
     */
    public static RequestPath[] getRequestPaths(PsiMethod psiMethod) {
        psiMethod.getModifierList();

        PsiAnnotation[] annotations = psiMethod.getModifierList().getAnnotations();
        List<RequestPath> list = new ArrayList<>();

        for (PsiAnnotation annotation : annotations) {
            if (isSupportedRequestMappingAnnotation(annotation)) {
                String defaultValue = "/";
                List<RequestPath> requestMappings = getRequestMappings(annotation, defaultValue);
                if (!requestMappings.isEmpty()) {
                    list.addAll(requestMappings);
                }
            }
        }

        return list.toArray(new RequestPath[0]);
    }


    private static String getRequestMappingValue(PsiAnnotation annotation) {
        String value = PsiAnnotationHelper.getAnnotationAttributeValue(annotation, "value");

//        String value = psiAnnotationMemberValue.getText().replace("\"","");
//        if(psiAnnotationMemberValue.)

        if (org.apache.commons.lang3.StringUtils.isEmpty(value))
            value = PsiAnnotationHelper.getAnnotationAttributeValue(annotation, "path");
        return value;
    }

    public static String[] getRequestMappingValues(PsiAnnotation annotation) {
        String[] values;
        //一个value class com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
        //多个value  class com.intellij.psi.impl.source.tree.java.PsiArrayInitializerMemberValueImpl
        PsiAnnotationMemberValue attributeValue = annotation.findDeclaredAttributeValue("value");

        if (attributeValue instanceof PsiLiteralExpression) {

            return new String[]{((PsiLiteralExpression) attributeValue).getValue().toString()};
        }
        if (attributeValue instanceof PsiArrayInitializerMemberValue) {
            PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) attributeValue).getInitializers();
            values = new String[initializers.length];

            for (PsiAnnotationMemberValue initializer : initializers) {

            }

            for (int i = 0; i < initializers.length; i++) {
                values[i] = ((PsiLiteralExpression) (initializers[i])).getValue().toString();
            }
        }

        return new String[]{};
    }


    public static String getOneRequestMappingPath(PsiClass psiClass) {
        // todo: 有必要 处理 PostMapping,GetMapping 么？
        PsiAnnotation annotation = PsiAnnotationHelper.findAnnotation(
                psiClass.getModifierList(), SpringRequestMethodAnnotation.REQUEST_MAPPING.getQualifiedName());

        String path = null;
        if (annotation != null) {
            path = RequestMappingAnnotationHelper.getRequestMappingValue(annotation);
        }

        return path != null ? path : "";
    }


    public static String getOneRequestMappingPath(PsiMethod psiMethod) {
//        System.out.println("psiMethod:::::::" + psiMethod);
        SpringRequestMethodAnnotation requestAnnotation = null;

        List<SpringRequestMethodAnnotation> springRequestAnnotations = Arrays.stream(SpringRequestMethodAnnotation.values()).filter(annotation ->
                PsiAnnotationHelper.findAnnotation(psiMethod.getModifierList(), annotation.getQualifiedName()) != null
        ).collect(Collectors.toList());

        if (!springRequestAnnotations.isEmpty()) {
            requestAnnotation = springRequestAnnotations.get(0);
        }

        String mappingPath;
        if (requestAnnotation != null) {
            PsiAnnotation annotation = PsiAnnotationHelper.findAnnotation(
                    psiMethod.getModifierList(), requestAnnotation.getQualifiedName());
            mappingPath = RequestMappingAnnotationHelper.getRequestMappingValue(annotation);
        } else {
            String methodName = psiMethod.getName();
            mappingPath = StringUtils.uncapitalize(methodName);
        }

        return mappingPath;
    }


}
