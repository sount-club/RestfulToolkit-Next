package com.sount.restful.search;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBList;
import com.sount.restful.navigation.action.RestServiceItem;

import java.awt.Component;
import java.awt.Container;
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

    public void testSourceLineIsRightAligned() {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText("ActivityAction.java", """
                package demo;

                public class ActivityAction {
                    public void rewardDetail() {}
                }
                """);
        PsiClass psiClass = javaFile.getClasses()[0];
        PsiMethod method = psiClass.findMethodsByName("rewardDetail", false)[0];
        RestServiceItem item = new RestServiceItem(method, "GET", "/activity/rewardDetail");

        UnifiedSearchRenderer renderer = new UnifiedSearchRenderer();
        JComponent component = (JComponent) renderer.getListCellRendererComponent(
                new JBList<>(),
                new SearchResult(item, 100, "url"),
                0,
                false,
                false
        );

        component.setBounds(0, 0, 460, component.getPreferredSize().height);
        layoutRecursively(component);

        JComponent sourceComponent = findComponentByName(component, "UnifiedSearchSource");
        assertNotNull(sourceComponent);
        assertTrue(sourceComponent.getX() > 0);
    }

    private static void layoutRecursively(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                layoutRecursively(child);
            }
        }
    }

    private static JComponent findComponentByName(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (child instanceof JComponent jComponent && name.equals(jComponent.getName())) {
                return jComponent;
            }
            if (child instanceof Container childContainer) {
                JComponent result = findComponentByName(childContainer, name);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
