package com.sount.restful.common;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PsiAnnotationHelper {
    private static final Logger LOG = Logger.getInstance(PsiAnnotationHelper.class);

    @NotNull
    public static List<String> getAnnotationAttributeValues(@Nullable PsiAnnotation annotation, String attr) {
        if (annotation == null) {
            return new ArrayList<>();
        }
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(attr);

        List<String> values = new ArrayList<>();
        //只有注解
        //一个值 class com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
        //多个值  class com.intellij.psi.impl.source.tree.java.PsiArrayInitializerMemberValueImpl
        if (value instanceof PsiReferenceExpression expression) {
            values.add(expression.getText());
        } else if (value instanceof PsiLiteralExpression expression) {
//            values.add(psiNameValuePair.getLiteralValue());
            Object literalValue = expression.getValue();
            if (literalValue != null) {
                values.add(literalValue.toString());
            }
        } else if (value instanceof PsiArrayInitializerMemberValue) {
            PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) value).getInitializers();

            for (PsiAnnotationMemberValue initializer : initializers) {
                values.add(initializer.getText().replace("\"", ""));
            }
        }

        return values;
    }

    public static String getAnnotationAttributeValue(@Nullable PsiAnnotation annotation, String attr) {
        List<String> values = getAnnotationAttributeValues(annotation, attr);
        if (!values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }

    public static @Nullable String getQualifiedName(@Nullable PsiAnnotation annotation) {
        if (annotation == null) {
            return null;
        }
        try {
            return annotation.getQualifiedName();
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Throwable e) {
            LOG.debug("Failed to resolve annotation qualified name", e);
            return null;
        }
    }

    public static boolean hasQualifiedName(@Nullable PsiAnnotation annotation, @NotNull String qualifiedName) {
        return qualifiedName.equals(getQualifiedName(annotation));
    }

    public static @Nullable PsiAnnotation findAnnotation(@Nullable PsiModifierList modifierList,
                                                         @NotNull String qualifiedName) {
        if (modifierList == null) {
            return null;
        }
        try {
            return modifierList.findAnnotation(qualifiedName);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Throwable e) {
            LOG.debug("Failed to find annotation " + qualifiedName, e);
            return null;
        }
    }
}
