package com.sount.restful.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.sount.restful.annotations.JaxrsHttpMethodAnnotation;
import com.sount.restful.annotations.SpringRequestMethodAnnotation;
import com.sount.restful.common.PsiAnnotationHelper;
import com.sount.restful.common.PsiMethodHelper;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * 方法级别 REST Action 的基类。
 * <p>
 * 仅在当前光标位于 Spring MVC / JAX-RS 注解方法上时才可见，
 * 用于 Generate URL、Generate QueryParam 等方法级操作。
 */
public abstract class SpringAnnotatedMethodAction extends AbstractBaseAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiMethod psiMethod = findTargetMethod(e);
        boolean visible = psiMethod != null &&
                (isRestController(psiMethod.getContainingClass()) || isRestfulMethod(psiMethod));
        setActionPresentationVisible(e, visible);
    }

    private boolean isRestController(PsiClass containingClass) {
        if (containingClass == null || containingClass.getModifierList() == null) {
            return false;
        }
        return PsiMethodHelper.isSpringRestSupported(containingClass);
    }

    private boolean isRestfulMethod(PsiMethod psiMethod) {
        final PsiModifierList modifierList = psiMethod.getModifierList();
        PsiAnnotation[] annotations = modifierList.getAnnotations();

        for (PsiAnnotation annotation : annotations) {
            boolean match = Arrays.stream(SpringRequestMethodAnnotation.values())
                    .map(SpringRequestMethodAnnotation::getQualifiedName)
                    .anyMatch(name -> PsiAnnotationHelper.hasQualifiedName(annotation, name));
            if (match) return true;
        }

        for (PsiAnnotation annotation : annotations) {
            boolean match = Arrays.stream(JaxrsHttpMethodAnnotation.values())
                    .map(JaxrsHttpMethodAnnotation::getQualifiedName)
                    .anyMatch(name -> PsiAnnotationHelper.hasQualifiedName(annotation, name));
            if (match) return true;
        }

        return PsiMethodHelper.isJaxrsRestSupported(psiMethod.getContainingClass());
    }
}
