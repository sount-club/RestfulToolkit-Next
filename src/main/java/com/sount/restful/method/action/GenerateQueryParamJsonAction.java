package com.sount.restful.method.action;

import java.awt.datatransfer.StringSelection;
import java.util.List;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.sount.restful.common.PsiMethodHelper;
import com.sount.restful.method.Parameter;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtNamedFunction;

/**
 * 生成Request Body JSON 字符串
 */
public class GenerateQueryParamJsonAction extends SpringAnnotatedMethodAction {

	@Override
	public void actionPerformed(AnActionEvent e) {

		//  @RequestBody entity 生成 json

		PsiMethod psiMethod = null;
		PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
		if (psiElement instanceof PsiMethod) {
			psiMethod = (PsiMethod) psiElement;
		}

		if (psiElement instanceof KtNamedFunction) {
			KtNamedFunction ktNamedFunction = (KtNamedFunction) psiElement;
			PsiElement parentPsi = psiElement.getParent().getParent();
			if (parentPsi instanceof KtClassOrObject) {
				List<PsiMethod> psiMethods = LightClassUtilsKt.toLightMethods(ktNamedFunction);
				psiMethod = psiMethods.get(0);
			}
		}

		PsiMethodHelper psiMethodHelper = PsiMethodHelper.create(psiMethod);
		List<Parameter> parameterList = psiMethodHelper.getParameterList();
		//JavaShortClassNameIndex.getInstance().get("Product",myProject(e), GlobalSearchScope.projectScope(myProject(e)))
		for (Parameter parameter : parameterList) {
			if (parameter.isRequestBodyFound()) {
				String queryJson = psiMethodHelper.buildRequestBodyJson(parameter);

				CopyPasteManager.getInstance().setContents(new StringSelection(queryJson));
				Editor myEditor = e.getData(CommonDataKeys.EDITOR);
				if (myEditor != null) {
					showPopupBalloon("复制成功", myEditor);
				}
				break;
			}
		}
	}

}
