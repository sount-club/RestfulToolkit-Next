package com.sount.restful.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.sount.restful.common.PsiClassHelper;

import java.awt.datatransfer.StringSelection;

/**
 * 将 Java / Kotlin 类转换为压缩 JSON（无换行和缩进）并复制到剪贴板。
 * <p>
 * 右键菜单 → "Convert to JSON (Compressed)"
 */
public class ConvertClassToJSONCompressedAction extends ConvertClassToJSONAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        PsiClass psiClass = getPsiClass(psiElement);
        if (psiClass == null) return;

        String json = PsiClassHelper.create(psiClass).convertClassToJSON(myProject(e), false);
        CopyPasteManager.getInstance().setContents(new StringSelection(json));
    }
}
