package com.sount.restful.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.sount.restful.common.PsiClassHelper;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;

public class ConvertClassToJSONAction extends AbstractBaseAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        PsiClass psiClass = findTargetClass(e);

        if(psiClass == null) return;

        String json = PsiClassHelper.create(psiClass).convertClassToJSON(myProject(e), true);
        CopyPasteManager.getInstance().setContents(new StringSelection(json));
    }

    @Nullable
    protected PsiClass getPsiClass(PsiElement psiElement) {
        return psiElement instanceof PsiClass ? (PsiClass) psiElement : null;
    }

    @Override
    public void update(AnActionEvent e) {
        setActionPresentationVisible(e, findTargetClass(e) != null);
    }
}
