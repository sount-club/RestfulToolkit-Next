package com.sount.restful.search;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBList;
import com.sount.restful.navigation.action.RestServiceItem;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JLabel;
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

    public void testModuleChipFontDoesNotShrinkAcrossRenders() {
        RestServiceItem item = createItem("GET", "/activity/rewardDetail");
        item.setModule(getModule());
        UnifiedSearchRenderer renderer = new UnifiedSearchRenderer();
        JBList<SearchResult> list = new JBList<>();

        renderer.getListCellRendererComponent(list, new SearchResult(item, 100, "url"), 0, false, false);
        JLabel moduleChip = findLabel(renderer, getModule().getName());
        float initialSize = moduleChip.getFont().getSize2D();

        for (int i = 0; i < 5; i++) {
            renderer.getListCellRendererComponent(list, new SearchResult(item, 100, "url"), i, false, false);
        }

        assertEquals(initialSize, moduleChip.getFont().getSize2D());
    }

    private RestServiceItem createItem(String methodText, String url) {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText("ActivityController.java", """
                package demo;

                public class ActivityController {
                    public void rewardDetail() {}
                }
                """);
        PsiClass psiClass = javaFile.getClasses()[0];
        PsiMethod method = psiClass.findMethodsByName("rewardDetail", false)[0];
        return new RestServiceItem(method, methodText, url);
    }

    private static JLabel findLabel(JComponent root, String text) {
        JLabel label = findLabelRecursive(root, text);
        if (label == null) {
            fail("Label not found: " + text);
        }
        return label;
    }

    private static JLabel findLabelRecursive(Component component, String text) {
        if (component instanceof JLabel label && text.equals(label.getText())) {
            return label;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JLabel label = findLabelRecursive(child, text);
                if (label != null) {
                    return label;
                }
            }
        }
        return null;
    }
}
