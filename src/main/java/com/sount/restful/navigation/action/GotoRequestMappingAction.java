package com.sount.restful.navigation.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.sount.restful.search.UnifiedSearchPopup;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;


public class GotoRequestMappingAction extends AnAction implements DumbAware {
    public GotoRequestMappingAction() {
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        String initialText = tryFindCopiedURL();

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Module currentModule = psiFile != null ? ModuleUtil.findModuleForPsiElement(psiFile) : null;

        UnifiedSearchPopup.show(project, initialText, currentModule);
    }

    private String tryFindCopiedURL() {
        String contents = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
        if (contents == null) {
            return null;
        }

        contents = contents.trim();
        if (contents.startsWith("http")) {
            if (contents.length() <= 120) {
                return contents;
            } else {
                return contents.substring(0, 120);
            }
        }

        return null;
    }
}
