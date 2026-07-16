package com.sount.restful.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.sount.restful.annotations.JaxrsHttpMethodAnnotation;
import com.sount.restful.annotations.JaxrsRequestAnnotation;
import com.sount.restful.annotations.SpringControllerAnnotation;
import com.sount.restful.annotations.SpringRequestMethodAnnotation;
import com.sount.restful.common.PsiAnnotationHelper;
import com.sount.restful.common.PsiMethodHelper;
import com.sount.restful.utils.RestfulToolkitBundle;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.Arrays;

/**
 * 生成并复制相对路径 URL（不含 host 和 port，含查询参数）。
 * <p>
 * 右键菜单 → "Generate && Copy Relation URL"
 */
public class GenerateUrlAction extends AbstractBaseAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        // Local variable, not an instance field: AnAction instances are singletons reused
        // across projects/sessions, so caching the Editor would leak cross-context state.
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiMethod psiMethod = findTargetMethod(e);
        if (psiMethod == null) return;

        String servicePath;
        if (isJaxrsRestMethod(psiMethod)) {
            servicePath = PsiMethodHelper.create(psiMethod).buildServiceUriPath();
        } else {
            servicePath = PsiMethodHelper.create(psiMethod).buildServiceUriPathWithParams();
        }
        if (servicePath == null) {
            return;
        }

        CopyPasteManager.getInstance().setContents(new StringSelection(servicePath));
        if (editor != null) {
            showPopupBalloon(RestfulToolkitBundle.message(RestfulToolkitBundle.Keys.ACTION_COPY_SUCCESS), editor);
        }
    }

    private boolean isJaxrsRestMethod(PsiMethod psiMethod) {
        PsiAnnotation[] annotations = psiMethod.getModifierList().getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            boolean match = Arrays.stream(JaxrsHttpMethodAnnotation.values())
                    .map(JaxrsHttpMethodAnnotation::getQualifiedName)
                    .anyMatch(name -> PsiAnnotationHelper.hasQualifiedName(annotation, name));
            if (match) return true;
        }
        return false;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiMethod psiMethod = findTargetMethod(e);
        boolean visible = psiMethod != null &&
                (isRestController(psiMethod.getContainingClass()) || isRestfulMethod(psiMethod));
        setActionPresentationVisible(e, visible);
    }

    private boolean isRestController(PsiClass containingClass) {
        if (containingClass == null) return false;
        PsiModifierList modifierList = containingClass.getModifierList();
        if (modifierList == null) return false;
        return PsiAnnotationHelper.findAnnotation(modifierList, SpringControllerAnnotation.REST_CONTROLLER.getQualifiedName()) != null ||
                PsiAnnotationHelper.findAnnotation(modifierList, SpringControllerAnnotation.CONTROLLER.getQualifiedName()) != null ||
                PsiAnnotationHelper.findAnnotation(modifierList, JaxrsRequestAnnotation.PATH.getQualifiedName()) != null;
    }

    private boolean isRestfulMethod(PsiMethod psiMethod) {
        PsiAnnotation[] annotations = psiMethod.getModifierList().getAnnotations();
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
        return false;
    }
}
