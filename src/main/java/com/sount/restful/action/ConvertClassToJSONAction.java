package com.sount.restful.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.sount.restful.common.PsiClassHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;

/**
 * 将 Java / Kotlin 类转换为格式化 JSON 并复制到剪贴板。
 * <p>
 * 右键菜单 → "Convert to JSON"
 */
public class ConvertClassToJSONAction extends AbstractBaseAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiClass psiClass = findTargetClass(e);
        if (psiClass == null) return;

        String json = PsiClassHelper.create(psiClass).convertClassToJSON(myProject(e), true);
        CopyPasteManager.getInstance().setContents(new StringSelection(json));
    }

    @Nullable
    protected PsiClass getPsiClass(PsiElement psiElement) {
        return psiElement instanceof PsiClass ? (PsiClass) psiElement : null;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        setActionPresentationVisible(e, findTargetClass(e) != null);
    }
}
