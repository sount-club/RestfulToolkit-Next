package com.sount.restful.search.ui;

import com.intellij.openapi.module.Module;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import com.sount.restful.utils.RestfulToolkitBundle;
import com.sount.restful.utils.RestfulToolkitBundle.Keys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 搜索弹窗筛选模型：负责在 Swing 控件值与领域筛选条件之间转换。
 */
final class SearchFilterModel {
    private SearchFilterModel() {
    }

    /**
     * 解析当前选中的 HTTP 方法；“全部”按钮返回 {@code null}。
     */
    static @Nullable HttpMethod resolveSelectedMethod(
            @NotNull ButtonGroup group,
            @NotNull EnumMap<HttpMethod, JToggleButton> buttons) {
        for (Map.Entry<HttpMethod, JToggleButton> entry : buttons.entrySet()) {
            if (entry.getValue().isSelected()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 根据模块下拉框选项解析真实模块对象。
     */
    static @Nullable Module resolveSelectedModule(@NotNull JComboBox<String> combo,
                                                   @Nullable Module currentModule,
                                                   @NotNull List<RestServiceItem> items) {
        Object selected = combo.getSelectedItem();
        if (selected == null || RestfulToolkitBundle.message(Keys.SEARCH_POPUP_MODULE_ALL).equals(selected)) {
            return null;
        }
        String selectedText = selected.toString();
        if (currentModule != null && selectedText.equals(formatCurrentModule(currentModule))) {
            return currentModule;
        }
        for (RestServiceItem item : items) {
            if (selectedText.equals(item.getModuleName())) {
                return item.getModule();
            }
        }
        return null;
    }

    /**
     * 用最新端点快照刷新模块筛选项，并尽可能恢复原选择。
     */
    static void refreshModuleFilter(@NotNull JComboBox<String> combo,
                                    @NotNull List<RestServiceItem> items,
                                    @Nullable Module currentModule,
                                    @Nullable Object preferredSelection) {
        combo.removeAllItems();
        String allModulesText = RestfulToolkitBundle.message(Keys.SEARCH_POPUP_MODULE_ALL);
        combo.addItem(allModulesText);
        if (currentModule != null) {
            combo.addItem(formatCurrentModule(currentModule));
        }

        Set<String> modules = new LinkedHashSet<>();
        for (RestServiceItem item : items) {
            String name = item.getModuleName();
            if (name != null && !name.isEmpty()
                    && (currentModule == null || !name.equals(currentModule.getName()))) {
                modules.add(name);
            }
        }
        List<String> sorted = new ArrayList<>(modules);
        Collections.sort(sorted);
        for (String moduleName : sorted) {
            combo.addItem(moduleName);
        }

        if (preferredSelection != null) {
            String selectedText = preferredSelection.toString();
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (selectedText.equals(combo.getItemAt(i))) {
                    combo.setSelectedIndex(i);
                    return;
                }
            }
        }
        combo.setSelectedItem(allModulesText);
    }

    /**
     * 生成“当前模块”筛选项的本地化展示文本。
     */
    static @NotNull String formatCurrentModule(@NotNull Module module) {
        return RestfulToolkitBundle.message(Keys.SEARCH_POPUP_MODULE_CURRENT, module.getName());
    }
}
