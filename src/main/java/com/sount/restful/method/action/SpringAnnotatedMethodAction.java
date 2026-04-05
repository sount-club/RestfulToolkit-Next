package com.sount.restful.method.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.sount.restful.annotations.JaxrsHttpMethodAnnotation;
import com.sount.restful.action.AbstractBaseAction;
import com.sount.restful.annotations.SpringControllerAnnotation;
import com.sount.restful.annotations.SpringRequestMethodAnnotation;
import com.sount.restful.common.PsiMethodHelper;

import java.util.Arrays;

/**
 * Restful method （restful 方法添加方法 ）
 */
public abstract class SpringAnnotatedMethodAction extends AbstractBaseAction {

    /**
     * spring rest 方法被选中才触发
     *
     * @param e
     */
    @Override
    public void update(AnActionEvent e) {
        PsiMethod psiMethod = findTargetMethod(e);
        boolean visible = psiMethod != null &&
                (isRestController(psiMethod.getContainingClass()) || isRestfulMethod(psiMethod));
        setActionPresentationVisible(e, visible);
    }

    //包含 "RestController" "Controller"
    private boolean isRestController(PsiClass containingClass) {
        if (containingClass == null || containingClass.getModifierList() == null) {
            return false;
        }
        PsiModifierList modifierList = containingClass.getModifierList();

        /*return modifierList.findAnnotation(SpringControllerAnnotation.REST_CONTROLLER.getQualifiedName()) != null ||
                modifierList.findAnnotation(SpringControllerAnnotation.CONTROLLER.getQualifiedName()) != null ;*/

        return modifierList.findAnnotation(SpringControllerAnnotation.REST_CONTROLLER.getQualifiedName()) != null ||
                modifierList.findAnnotation(SpringControllerAnnotation.CONTROLLER.getQualifiedName()) != null /*||
                modifierList.findAnnotation(JaxrsRequestAnnotation.PATH.getQualifiedName()) != null*/;
    }

    private boolean isRestfulMethod(PsiMethod psiMethod) {
        final PsiModifierList modifierList = psiMethod.getModifierList();
        if (modifierList == null) {
            return false;
        }
        PsiAnnotation[] annotations = modifierList.getAnnotations();

        for (PsiAnnotation annotation : annotations) {
            boolean match = Arrays.stream(SpringRequestMethodAnnotation.values()).map(sra -> sra.getQualifiedName()).anyMatch(name -> name.equals(annotation.getQualifiedName()));
            if (match) return match;
        }

        for (PsiAnnotation annotation : annotations) {
            boolean match = Arrays.stream(JaxrsHttpMethodAnnotation.values()).map(sra -> sra.getQualifiedName()).anyMatch(name -> name.equals(annotation.getQualifiedName()));
            if (match) return true;
        }

        return PsiMethodHelper.isJaxrsRestSupported(psiMethod.getContainingClass());
    }


}
