package com.sount.restful.popup.action;


import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;

/**
 * @author Olivier Smedile
 * @version $Id: File Header.java 3 2008-03-11 08:52:55Z osmedile $
 */
public class PopupChoiceAction extends DumbAwareAction {
    private final ActionGroup actionGroup;


    public PopupChoiceAction() {
        actionGroup = (ActionGroup) ActionManager.getInstance().getAction("RestfulToolkitGroup");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (actionGroup == null) {
            return;
        }
        ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, actionGroup,
                e.getDataContext(), JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, false);

        popup.showInBestPositionFor(e.getDataContext());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
        if (editor == null) {
            e.getPresentation().setEnabled(false);
            return;
        }
        Project project = getEventProject(e);
        if (project != null) {
            e.getPresentation().setEnabled(LookupManager.getInstance(project).getActiveLookup() == null);
        }
    }
}
