package com.sount.restful.method.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiMethod;
import com.sount.restful.common.PsiMethodHelper;

import java.awt.datatransfer.StringSelection;

/**
 * 生成并复制restful url
 * tood: 没考虑RequestMapping 多个值的情况
 */
public class
GenerateFullUrlAction extends SpringAnnotatedMethodAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Module module = myModule(e);
        PsiMethod psiMethod = findTargetMethod(e);
        if (psiMethod == null) {
            return;
        }

        String url = PsiMethodHelper.create(psiMethod).withModule(module).buildFullUrlWithParams();
        CopyPasteManager.getInstance().setContents(new StringSelection(url));
        Editor myEditor = e.getData(CommonDataKeys.EDITOR);
        if (myEditor != null) {
            showPopupBalloon("复制成功", myEditor);
        }

    }

}
