package com.sount.restful.action;

import java.awt.*;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.JBColor;
import org.jspecify.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtil;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtNamedFunction;

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

	@Nullable
	protected PsiElement findContextPsiElement(AnActionEvent e) {
		PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
		if (psiElement != null) {
			return psiElement;
		}

		PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
		Editor editor = e.getData(CommonDataKeys.EDITOR);
		if (psiFile != null && editor != null) {
			return psiFile.findElementAt(editor.getCaretModel().getOffset());
		}

		return null;
	}

	@Nullable
	protected PsiMethod findTargetMethod(AnActionEvent e) {
		PsiElement psiElement = findContextPsiElement(e);
		if (psiElement == null) {
			return null;
		}
		if (psiElement instanceof PsiMethod psiMethod) {
			return psiMethod;
		}

		KtNamedFunction ktNamedFunction = PsiTreeUtil.getParentOfType(psiElement, KtNamedFunction.class, false);
		if (ktNamedFunction != null) {
			java.util.List<PsiMethod> psiMethods = LightClassUtilsKt.toLightMethods(ktNamedFunction);
			if (!psiMethods.isEmpty()) {
				return psiMethods.get(0);
			}
		}

		return PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class, false);
	}

	@Nullable
	protected PsiClass findTargetClass(AnActionEvent e) {
		PsiElement psiElement = findContextPsiElement(e);
		if (psiElement == null) {
			return null;
		}
		if (psiElement instanceof PsiClass psiClass) {
			return psiClass;
		}

		KtClassOrObject ktClassOrObject = PsiTreeUtil.getParentOfType(psiElement, KtClassOrObject.class, false);
		if (ktClassOrObject != null && LightClassUtil.INSTANCE.canGenerateLightClass(ktClassOrObject)) {
			return LightClassUtilsKt.toLightClass(ktClassOrObject);
		}

		return PsiTreeUtil.getParentOfType(psiElement, PsiClass.class, false);
	}

}
