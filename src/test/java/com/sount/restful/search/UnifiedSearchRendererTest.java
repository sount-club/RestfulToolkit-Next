package com.sount.restful.search;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBList;
import com.sount.restful.navigation.action.RestServiceItem;

import java.awt.Component;
import javax.swing.JComponent;

public class UnifiedSearchRendererTest extends BasePlatformTestCase {

    public void testRendererKeepsFullLongSourceInTooltip() {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText("VeryLongActivityRewardConfigurationControllerName.java", """
                package demo;

                public class VeryLongActivityRewardConfigurationControllerName {
                    public void queryRewardDetailWithExtremelyLongMethodNameForAdminModule() {}
                }
                """);
        PsiClass psiClass = javaFile.getClasses()[0];
        PsiMethod method = psiClass.findMethodsByName("queryRewardDetailWithExtremelyLongMethodNameForAdminModule", false)[0];
        RestServiceItem item = new RestServiceItem(method, "GET", "/activity/rewardDetail");

        UnifiedSearchRenderer renderer = new UnifiedSearchRenderer();
        Component component = renderer.getListCellRendererComponent(
                new JBList<>(),
                new SearchResult(item, 100, "url"),
                0,
                false,
                false
        );

        assertTrue(((JComponent) component).getToolTipText().contains(
                "VeryLongActivityRewardConfigurationControllerName#queryRewardDetailWithExtremelyLongMethodNameForAdminModule"
        ));
        assertTrue(component.getPreferredSize().height >= 36);
    }
}
