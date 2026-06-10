package com.sount.restful.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiMethod;
import com.sount.restful.common.PsiMethodHelper;
import com.sount.restful.utils.RestfulToolkitBundle;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

/**
 * 生成并复制完整 REST URL（含 host、port、context-path 和查询参数）。
 * <p>
 * 右键菜单 → "Generate && Copy Full URL"
 */
public class GenerateFullUrlAction extends SpringAnnotatedMethodAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Module module = myModule(e);
        PsiMethod psiMethod = findTargetMethod(e);
        if (psiMethod == null) {
            return;
        }

        String url = PsiMethodHelper.create(psiMethod).withModule(module).buildFullUrlWithParams();
        if (url == null) {
            return;
        }
        CopyPasteManager.getInstance().setContents(new StringSelection(url));
        Editor myEditor = e.getData(CommonDataKeys.EDITOR);
        if (myEditor != null) {
            showPopupBalloon(RestfulToolkitBundle.message(RestfulToolkitBundle.Keys.ACTION_COPY_SUCCESS), myEditor);
        }
    }
}
