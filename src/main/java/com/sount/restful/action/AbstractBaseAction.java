package com.sount.restful.action;

import java.awt.*;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.JBColor;
import org.jspecify.annotations.NonNull;

public abstract class AbstractBaseAction extends AnAction {

	@Override
	public @NonNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	protected Module myModule(AnActionEvent e) {
		return e.getData(PlatformDataKeys.MODULE);
	}

	protected Project myProject(AnActionEvent e) {
		return getEventProject(e);
	}

	/**
	 * 设置触发有效条件
	 *
	 * @param e
	 * @param visible
	 */
	protected void setActionPresentationVisible(AnActionEvent e, boolean visible) {
		e.getPresentation().setVisible(visible);
	}

	protected void showPopupBalloon(final String result, final Editor myEditor) {
		ApplicationManager.getApplication().invokeLater(new Runnable() {
			@Override
			public void run() {
				JBPopupFactory factory = JBPopupFactory.getInstance();
				factory.createHtmlTextBalloonBuilder(result, null, new JBColor(new Color(186, 238, 186), new Color(73, 117, 73)), null)
						.setFadeoutTime(1000)
						.createBalloon()
						.show(factory.guessBestPopupLocation(myEditor), Balloon.Position.atRight);
			}
		});
	}

}
