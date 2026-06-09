package com.sount.restful.common;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.sount.restful.method.HttpMethod;

import javax.swing.*;

public class ToolkitIcons {

    public static class METHOD {

        public static Icon get(HttpMethod method) {
            if (method == null) {
                return UNDEFINED;
            }
            return switch (method) {
                case GET -> GET;
                case POST -> POST;
                case PUT -> PUT;
                case PATCH -> PATCH;
                case DELETE -> DELETE;
                default -> UNDEFINED;
            };
        }

        public static final Icon GET = IconLoader.getIcon("/icons/method/get.png", ToolkitIcons.class); // 16x16 GREEN
        // post put patch
        public static final Icon PUT = IconLoader.getIcon("/icons/method/put.png", ToolkitIcons.class); // 16x16 ORANGE
        public static final Icon POST = IconLoader.getIcon("/icons/method/post.png", ToolkitIcons.class); // 16x16 BLUE
        public static final Icon PATCH = IconLoader.getIcon("/icons/method/patch.png", ToolkitIcons.class); // 16x16 GRAY
        public static final Icon DELETE = IconLoader.getIcon("/icons/method/delete.png", ToolkitIcons.class); // 16x16 RED
        public static final Icon UNDEFINED = IconLoader.getIcon("/icons/method/undefined.png", ToolkitIcons.class); // 16x16 GRAY
        // OPTIONS HEAD
    }

    // public static final Icon MODULE = AllIcons.Modules.ModulesNode; // 16x16
    public static final Icon MODULE = AllIcons.Nodes.ModuleGroup; // 16x16
    public static final Icon Refresh = AllIcons.Actions.Refresh; // 16x16
    //    public static final Icon SERVICE = IconLoader.getIcon("/icons/service.png"); // 16x16

    public static final Icon SERVICE = IconLoader.getIcon("/icons/restService.svg", ToolkitIcons.class); // 16x16

}
