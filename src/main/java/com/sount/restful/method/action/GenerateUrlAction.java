package com.sount.restful.method.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.sount.restful.action.AbstractBaseAction;
import com.sount.restful.annotations.JaxrsHttpMethodAnnotation;
import com.sount.restful.annotations.JaxrsRequestAnnotation;
import com.sount.restful.annotations.SpringControllerAnnotation;
import com.sount.restful.annotations.SpringRequestMethodAnnotation;
import com.sount.restful.common.PsiMethodHelper;
import com.sount.utils.RestfulToolkitBundle;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.Arrays;
import java.util.Objects;

/**
 * 生成并复制restful url
 * todo: 没考虑RequestMapping 多个值的情况
 */
public class GenerateUrlAction /*extends RestfulMethodSpringSupportedAction*/ extends AbstractBaseAction {
    Editor myEditor;

    @Override
    public void actionPerformed(AnActionEvent e) {
        myEditor = e.getData(CommonDataKeys.EDITOR);
        PsiMethod psiMethod = findTargetMethod(e);
        if (psiMethod == null) return;

        //TODO: 需完善 jaxrs 支持
        String servicePath;
        if (isJaxrsRestMethod(psiMethod)) {
            servicePath = PsiMethodHelper.create(psiMethod).buildServiceUriPath();
        } else {
            servicePath = PsiMethodHelper.create(psiMethod).buildServiceUriPathWithParams();
        }

        CopyPasteManager.getInstance().setContents(new StringSelection(servicePath));
        showPopupBalloon(RestfulToolkitBundle.message(RestfulToolkitBundle.Keys.ACTION_COPY_SUCCESS), myEditor);
    }

    private boolean isJaxrsRestMethod(PsiMethod psiMethod) {
        PsiAnnotation[] annotations = psiMethod.getModifierList().getAnnotations();

        for (PsiAnnotation annotation : annotations) {
            boolean match = Arrays.stream(JaxrsHttpMethodAnnotation.values()).map(JaxrsHttpMethodAnnotation::getQualifiedName).anyMatch(name -> name.equals(annotation.getQualifiedName()));
            if (match) {
                return true;
            }
        }

        return false;
    }

    /**
     * spring rest 方法被选中才触发
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiMethod psiMethod = findTargetMethod(e);
        boolean visible = psiMethod != null &&
                (isRestController(Objects.requireNonNull(psiMethod.getContainingClass())) || isRestfulMethod(psiMethod));
        setActionPresentationVisible(e, visible);
    }

    //包含 "RestController" "Controller"
    private boolean isRestController(PsiClass containingClass) {
        PsiModifierList modifierList = containingClass.getModifierList();

        if (modifierList == null) {
            return false;
        }
        return modifierList.findAnnotation(SpringControllerAnnotation.REST_CONTROLLER.getQualifiedName()) != null ||
                modifierList.findAnnotation(SpringControllerAnnotation.CONTROLLER.getQualifiedName()) != null ||
                modifierList.findAnnotation(JaxrsRequestAnnotation.PATH.getQualifiedName()) != null;
    }

    private boolean isRestfulMethod(PsiMethod psiMethod) {
        PsiAnnotation[] annotations = psiMethod.getModifierList().getAnnotations();

        for (PsiAnnotation annotation : annotations) {
            boolean match = Arrays.stream(SpringRequestMethodAnnotation.values()).map(SpringRequestMethodAnnotation::getQualifiedName).anyMatch(name -> name.equals(annotation.getQualifiedName()));
            if (match) {
                return true;
            }
        }

        for (PsiAnnotation annotation : annotations) {
            boolean match = Arrays.stream(JaxrsHttpMethodAnnotation.values()).map(JaxrsHttpMethodAnnotation::getQualifiedName).anyMatch(name -> name.equals(annotation.getQualifiedName()));
            if (match) {
                return true;
            }
        }

        return false;
    }

}
