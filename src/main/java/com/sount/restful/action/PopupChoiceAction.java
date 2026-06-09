package com.sount.restful.action;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;

/**
 * 主菜单栏中的 RestfulToolkit Next 弹出菜单入口。
 * <p>
 * 将 {@code RestfulToolkitGroup}（右键菜单中的所有子动作）
 * 以弹出列表的形式展示，挂在主菜单栏末尾。
 * 代码补全激活时自动禁用，避免快捷键冲突。
 */
public class PopupChoiceAction extends DumbAwareAction {
    private final ActionGroup actionGroup;

    public PopupChoiceAction() {
        actionGroup = (ActionGroup) ActionManager.getInstance().getAction("RestfulToolkitGroup");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (actionGroup == null) return;
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
