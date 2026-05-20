package com.sount.restful.navigator;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.sount.restful.navigation.action.RestServiceItem;
import com.sount.utils.RestServiceDataKeys;

import java.util.List;

public class EditSourceAction extends AnAction implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    Presentation p = e.getPresentation();
    p.setVisible(isVisible(e));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    List<RestServiceItem> serviceItems = RestServiceDataKeys.SERVICE_ITEMS.getData(e.getDataContext());
    if (serviceItems == null) return;

    for (RestServiceItem serviceItem : serviceItems) {
      PsiElement psiElement = serviceItem.getPsiElement();
      if (psiElement != null && psiElement.isValid()) {
        serviceItem.navigate(true);
      }
    }
  }


  protected boolean isVisible(AnActionEvent e) {
    return true;
  }
}