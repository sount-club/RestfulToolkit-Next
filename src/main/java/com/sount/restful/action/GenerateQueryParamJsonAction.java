package com.sount.restful.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiMethod;
import com.sount.restful.common.PsiMethodHelper;
import com.sount.restful.method.Parameter;
import com.sount.restful.utils.RestfulToolkitBundle;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * 生成并复制 @RequestBody 参数的 JSON 字符串。
 * <p>
 * 右键菜单 → "Generate && Copy RequestBody (JSON)"
 */
public class GenerateQueryParamJsonAction extends SpringAnnotatedMethodAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiMethod psiMethod = findTargetMethod(e);
        if (psiMethod == null) return;

        PsiMethodHelper psiMethodHelper = PsiMethodHelper.create(psiMethod);
        List<Parameter> parameterList = psiMethodHelper.getParameterList();
        for (Parameter parameter : parameterList) {
            if (parameter.isRequestBodyFound()) {
                String queryJson = psiMethodHelper.buildRequestBodyJson(parameter);
                CopyPasteManager.getInstance().setContents(new StringSelection(queryJson));
                Editor myEditor = e.getData(CommonDataKeys.EDITOR);
                if (myEditor != null) {
                    showPopupBalloon(RestfulToolkitBundle.message(RestfulToolkitBundle.Keys.ACTION_COPY_SUCCESS), myEditor);
                }
                break;
            }
        }
    }
}
