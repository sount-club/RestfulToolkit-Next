package com.sount.restful.common.jaxrs;


import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.sount.restful.annotations.JaxrsHttpMethodAnnotation;
import com.sount.restful.annotations.JaxrsPathAnnotation;
import com.sount.restful.common.PsiAnnotationHelper;
import com.sount.restful.method.RequestPath;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class JaxrsAnnotationHelper {

    private static String getWsPathValue(PsiAnnotation annotation) {
        String value = PsiAnnotationHelper.getAnnotationAttributeValue(annotation, "value");

        return value != null ? value : "";
    }

    /**
     * 过滤所有注解
     */
    public static RequestPath[] getRequestPaths(PsiMethod psiMethod) {
        PsiAnnotation[] annotations = psiMethod.getModifierList().getAnnotations();
        List<RequestPath> list = new ArrayList<>();

        PsiAnnotation wsPathAnnotation = psiMethod.getModifierList().findAnnotation(JaxrsPathAnnotation.PATH.getQualifiedName());
        String path = wsPathAnnotation == null ? psiMethod.getName() : getWsPathValue(wsPathAnnotation);

        JaxrsHttpMethodAnnotation[] jaxrsHttpMethodAnnotations = JaxrsHttpMethodAnnotation.values();

        Arrays.stream(annotations)
                .forEach(a -> Arrays.stream(jaxrsHttpMethodAnnotations)
                        .forEach(methodAnnotation -> {
                            if (Objects.equals(a.getQualifiedName(), methodAnnotation.getQualifiedName())) {
                                list.add(new RequestPath(path, methodAnnotation.getShortName()));
                            }
                        })
                );

        return list.toArray(new RequestPath[0]);
    }


    public static String getClassUriPath(PsiClass psiClass) {
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList == null) {
            return "";
        }
        PsiAnnotation annotation = modifierList.findAnnotation(JaxrsPathAnnotation.PATH.getQualifiedName());
        String path = PsiAnnotationHelper.getAnnotationAttributeValue(annotation, "value");
        return path != null ? path : "";
    }


    public static String getMethodUriPath(PsiMethod psiMethod) {
        JaxrsHttpMethodAnnotation requestAnnotation = null;

        List<JaxrsHttpMethodAnnotation> httpMethodAnnotations = Arrays.stream(JaxrsHttpMethodAnnotation.values())
                .filter(annotation -> psiMethod.getModifierList().findAnnotation(annotation.getQualifiedName()) != null)
                .toList();

        if (!httpMethodAnnotations.isEmpty()) {
            requestAnnotation = httpMethodAnnotations.getFirst();
        }

        String mappingPath;
        if (requestAnnotation != null) {
            PsiAnnotation annotation = psiMethod.getModifierList().findAnnotation(JaxrsPathAnnotation.PATH.getQualifiedName());
            mappingPath = getWsPathValue(annotation);
        } else {
            String methodName = psiMethod.getName();
            mappingPath = StringUtils.uncapitalize(methodName);
        }

        return mappingPath;
    }

}
