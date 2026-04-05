package com.sount.restful.method.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiMethod;
import com.sount.restful.common.PsiMethodHelper;

import java.awt.datatransfer.StringSelection;

/**
 * 生成查询参数
 */
public class GenerateQueryParamAction extends SpringAnnotatedMethodAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        PsiMethod psiMethod = findTargetMethod(e);

        if (psiMethod != null) {
            String params = PsiMethodHelper.create(psiMethod).buildParamString();

            CopyPasteManager.getInstance().setContents(new StringSelection(params));
            Editor myEditor = e.getData(CommonDataKeys.EDITOR);
            if (myEditor != null) {
                showPopupBalloon("复制成功" , myEditor);
            }
        }

    }

}
