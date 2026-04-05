package com.sount.restful.method.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.*;
import com.sount.restful.action.AbstractBaseAction;
import com.sount.restful.annotations.JaxrsHttpMethodAnnotation;
import com.sount.restful.annotations.JaxrsRequestAnnotation;
import com.sount.restful.annotations.SpringControllerAnnotation;
import com.sount.restful.annotations.SpringRequestMethodAnnotation;
import com.sount.restful.common.PsiMethodHelper;

import java.awt.datatransfer.StringSelection;
import java.util.Arrays;

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
        showPopupBalloon("复制成功", myEditor);
    }

    private boolean isJaxrsRestMethod(PsiMethod psiMethod) {
        PsiAnnotation[] annotations = psiMethod.getModifierList().getAnnotations();

        for (PsiAnnotation annotation : annotations) {
            boolean match = Arrays.stream(JaxrsHttpMethodAnnotation.values()).map(sra -> sra.getQualifiedName()).anyMatch(name -> name.equals(annotation.getQualifiedName()));
            if (match) {
                return match;
            }
        }

        return false;
    }

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
        PsiModifierList modifierList = containingClass.getModifierList();

        /*return modifierList.findAnnotation(SpringControllerAnnotation.REST_CONTROLLER.getQualifiedName()) != null ||
                modifierList.findAnnotation(SpringControllerAnnotation.CONTROLLER.getQualifiedName()) != null ;*/

        return modifierList.findAnnotation(SpringControllerAnnotation.REST_CONTROLLER.getQualifiedName()) != null ||
                modifierList.findAnnotation(SpringControllerAnnotation.CONTROLLER.getQualifiedName()) != null ||
                modifierList.findAnnotation(JaxrsRequestAnnotation.PATH.getQualifiedName()) != null;
    }

    private boolean isRestfulMethod(PsiMethod psiMethod) {
        PsiAnnotation[] annotations = psiMethod.getModifierList().getAnnotations();

        for (PsiAnnotation annotation : annotations) {
            boolean match = Arrays.stream(SpringRequestMethodAnnotation.values()).map(sra -> sra.getQualifiedName()).anyMatch(name -> name.equals(annotation.getQualifiedName()));
            if (match) {
                return match;
            }
        }

        for (PsiAnnotation annotation : annotations) {
            boolean match = Arrays.stream(JaxrsHttpMethodAnnotation.values()).map(sra -> sra.getQualifiedName()).anyMatch(name -> name.equals(annotation.getQualifiedName()));
            if (match) {
                return match;
            }
        }

        return false;
    }


    // private void showPopupBalloon(final String result) {
    //     ApplicationManager.getApplication().invokeLater(new Runnable() {
    //         @Override
    //         public void run() {
    //             JBPopupFactory factory = JBPopupFactory.getInstance();
    //             factory.createHtmlTextBalloonBuilder(result, null, new JBColor(new Color(186, 238, 186), new Color(73, 117, 73)), null)
    //                     .setFadeoutTime(5000)
    //                     .createBalloon()
    //                     .show(factory.guessBestPopupLocation(myEditor), Balloon.Position.above);
    //         }
    //     });
    // }

}
