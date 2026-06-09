package com.sount.restful.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.sount.restful.common.PsiClassHelper;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将 Java / Kotlin 类的字段转换为 Bulk Value 格式（{@code key:value} 逐行）并复制到剪贴板。
 * <p>
 * 适用于 Postman Bulk Edit 等场景。右键菜单 → "Convert to Bulk Value"
 */
public class ConvertClassToBulkValueAction extends AbstractBaseAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiClass psiClass = findTargetClass(e);
        if (psiClass == null) return;

        final List<PsiField> fields = getFields(psiClass);
        if (fields != null && !fields.isEmpty()) {
            StringBuilder stringBuilder = new StringBuilder();
            for (PsiField field : fields) {
                final String fieldName = field.getName();
                if ("serialVersionUID".equals(fieldName)) continue;
                stringBuilder.append(String.format("%s:%s\r\n", fieldName,
                        PsiClassHelper.getJavaBaseTypeDefaultValue(field.getType().getPresentableText())));
            }
            CopyPasteManager.getInstance().setContents(new StringSelection(stringBuilder.toString()));
        }
    }

    protected List<PsiClass> getPsiClassLinkList(PsiClass psiClass) {
        List<PsiClass> psiClassList = new ArrayList<>();
        PsiClass currentClass = psiClass;
        while (null != currentClass && !"Object".equals(currentClass.getName())) {
            psiClassList.add(currentClass);
            currentClass = currentClass.getSuperClass();
        }
        Collections.reverse(psiClassList);
        return psiClassList;
    }

    protected List<PsiField> getFields(PsiClass psiClass) {
        final List<PsiClass> psiClassLinkList = getPsiClassLinkList(psiClass);
        List<PsiField> fields = new ArrayList<>();
        for (PsiClass pc : psiClassLinkList) {
            Collections.addAll(fields, pc.getFields());
        }
        return fields;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        setActionPresentationVisible(e, findTargetClass(e) != null);
    }
}
