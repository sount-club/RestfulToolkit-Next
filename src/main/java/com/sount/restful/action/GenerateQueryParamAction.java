package com.sount.restful.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiMethod;
import com.sount.restful.common.PsiMethodHelper;
import com.sount.restful.utils.RestfulToolkitBundle;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

/**
 * 生成并复制 Query 参数（Key=Value 格式，适用于 Postman Bulk Edit 等场景）。
 * <p>
 * 右键菜单 → "Generate && Copy Query Param (Key Value)"
 */
public class GenerateQueryParamAction extends SpringAnnotatedMethodAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiMethod psiMethod = findTargetMethod(e);
        if (psiMethod != null) {
            String params = PsiMethodHelper.create(psiMethod).buildParamString();
            CopyPasteManager.getInstance().setContents(new StringSelection(params));
            Editor myEditor = e.getData(CommonDataKeys.EDITOR);
            if (myEditor != null) {
                showPopupBalloon(RestfulToolkitBundle.message(RestfulToolkitBundle.Keys.ACTION_COPY_SUCCESS), myEditor);
            }
        }
    }
}
