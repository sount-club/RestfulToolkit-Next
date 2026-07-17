package com.sount.restful.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.sount.restful.search.ui.UnifiedSearchPopup;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;

/**
 * Ctrl+\ 快捷键入口，打开统一搜索弹窗定位 REST 端点。
 * <p>
 * 自动从剪贴板读取 HTTP URL 并提取路径作为预填搜索文本，
 * 同时根据当前编辑器文件确定默认模块过滤。
 */
public class GotoRequestMappingAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        String initialText = tryFindCopiedURL();

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Module currentModule = psiFile != null ? ModuleUtil.findModuleForPsiElement(psiFile) : null;

        UnifiedSearchPopup.show(project, initialText, currentModule);
    }

    private String tryFindCopiedURL() {
        String contents = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
        if (contents == null) return null;

        contents = contents.trim();
        if (contents.startsWith("http://") || contents.startsWith("https://")) {
            return extractPath(contents);
        }

        return null;
    }

    /**
     * 从完整 HTTP URL 中提取路径部分，去掉 scheme、host、port、query 和 fragment。
     * <p>
     * {@code http://localhost:8080/api/users?id=123#list} → {@code /api/users}
     */
    static @NotNull String extractPath(@NotNull String url) {
        int start = url.indexOf("://");
        if (start < 0) return url;
        start += 3;

        int pathStart = url.indexOf('/', start);
        if (pathStart < 0) return "/";

        int pathEnd = url.length();
        int queryStart = url.indexOf('?', pathStart);
        if (queryStart >= 0) pathEnd = queryStart;
        int fragmentStart = url.indexOf('#', pathStart);
        if (fragmentStart >= 0 && fragmentStart < pathEnd) pathEnd = fragmentStart;

        String path = url.substring(pathStart, pathEnd);
        return path.isEmpty() ? "/" : path;
    }
}
