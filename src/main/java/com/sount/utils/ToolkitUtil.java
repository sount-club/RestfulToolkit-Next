package com.sount.utils;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class ToolkitUtil {

    public static boolean isNoBackgroundMode() {
        return (ApplicationManager.getApplication().isUnitTestMode()
                || ApplicationManager.getApplication().isHeadlessEnvironment());
    }

    public static void runWhenProjectIsReady(final Project project, final Runnable runnable) {
        DumbService.getInstance(project).smartInvokeLater(runnable);
    }

    public static void invokeLater(Runnable r) {
        ApplicationManager.getApplication().invokeLater(r);
    }

    public static void invokeLater(Project p, Runnable r) {
        invokeLater(p, ModalityState.defaultModalityState(), r);
    }

    public static void invokeLater(final Project p, final ModalityState state, final Runnable r) {
        if (isNoBackgroundMode()) {
            r.run();
        } else {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!p.isDisposed()) {
                    r.run();
                }
            }, state);
        }
    }


    public static String formatHtmlImage(URL url) {
        return "<img src=\"" + url + "\"> ";
    }

    public static void runWriteAction(@NotNull Runnable action) {
        ApplicationManager.getApplication().runWriteAction(action);
    }


    @NotNull
    public static String textToRequestParam(String text) {
        StringBuilder param = new StringBuilder();

        Map<String, String> paramMap = textToParamMap(text);

        if (!paramMap.isEmpty()) {
            paramMap.forEach((s, o) -> param.append(s).append("=").append(o).append("&"));
        }

        return param.isEmpty() ? "" : param.deleteCharAt(param.length() - 1).toString();
    }


    @NotNull
    public static Map<String, String> textToParamMap(String text) {
        Map<String, String> paramMap = new HashMap<>();
        String[] lines = text.split("\n");

        for (String line : lines) {
            if (!line.startsWith("//") && line.contains(":")) {

                String[] prop = line.split(":");

                if (prop.length > 1) {
                    String key = prop[0].trim();
                    String value = prop[1].trim();
                    paramMap.put(key, value);
                }
            }
        }
        return paramMap;
    }


}
