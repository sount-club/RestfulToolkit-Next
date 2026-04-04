package com.sount.restful.navigator;


import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.sount.restful.navigation.action.RestServiceItem;
import com.sount.utils.RestServiceDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class RestServicesNavigatorPanel extends SimpleToolWindowPanel implements UiDataProvider {

    private final Project myProject;
    private final JTree myTree;
    RestServiceDetail myRestServiceDetail;

    private Splitter servicesContentPaneSplitter;

    public RestServicesNavigatorPanel(Project project, JTree tree) {
        super(true, true);

        myProject = project;
        myTree = tree;
        myRestServiceDetail = project.getService(RestServiceDetail.class);

        final ActionManager actionManager = ActionManager.getInstance();
        ActionToolbar actionToolbar = actionManager.createActionToolbar("RestToolkit Navigator Toolbar",
                (DefaultActionGroup) actionManager
                        .getAction("Toolkit.NavigatorActionsToolbar"),
                true);
        setToolbar(actionToolbar.getComponent());

        Color gray = new Color(36, 38, 39);

        myTree.setBorder(BorderFactory.createLineBorder(gray));
        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.RED));

        servicesContentPaneSplitter = new Splitter(true, 0.5f);
        servicesContentPaneSplitter.setShowDividerControls(true);
        servicesContentPaneSplitter.setDividerWidth(10);
        servicesContentPaneSplitter.setBorder(BorderFactory.createLineBorder(Color.RED));

        servicesContentPaneSplitter.setFirstComponent(scrollPane);
        servicesContentPaneSplitter.setSecondComponent(myRestServiceDetail);

        setContent(servicesContentPaneSplitter);

        // popup
        myTree.addMouseListener(new PopupHandler() {
            @Override
            public void invokePopup(final Component comp, final int x, final int y) {
                final String id = getMenuIdForSelectedNodes();
                if (id != null) {
                    final ActionGroup actionGroup = (ActionGroup) actionManager.getAction(id);
                    if (actionGroup != null) {
                        JPopupMenu component = actionManager.createActionPopupMenu("", actionGroup).getComponent();
                        component.show(comp, x, y);
                    }
                }
            }
        });
    }

    private String getMenuIdForSelectedNodes() {
        List<RestServiceStructure.BaseSimpleNode> selectedNodes = RestServiceStructure.getSelectedNodes(myTree, RestServiceStructure.BaseSimpleNode.class);
        return getMenuId(selectedNodes);
    }

    @Nullable
    private String getMenuId(Collection<? extends RestServiceStructure.BaseSimpleNode> nodes) {
        String id = null;
        for (RestServiceStructure.BaseSimpleNode node : nodes) {
            String menuId = node.getMenuId();
            if (menuId == null) {
                return null;
            }
            if (id == null) {
                id = menuId;
            } else if (!id.equals(menuId)) {
                return null;
            }
        }
        return id;
    }

    private Collection<? extends RestServiceStructure.BaseSimpleNode> getSelectedNodes(Class<RestServiceStructure.BaseSimpleNode> aClass) {
        return RestServiceStructure.getSelectedNodes(myTree, aClass);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
        sink.set(RestServiceDataKeys.SERVICE_ITEMS, extractServices());
        super.uiDataSnapshot(sink);
    }

    private List<RestServiceItem> extractServices() {
        List<RestServiceItem> result = new ArrayList<>();

        Collection<? extends RestServiceStructure.BaseSimpleNode> selectedNodes = getSelectedNodes(RestServiceStructure.BaseSimpleNode.class);
        for (RestServiceStructure.BaseSimpleNode selectedNode : selectedNodes) {
            if (selectedNode instanceof RestServiceStructure.ServiceNode) {
                result.add(((RestServiceStructure.ServiceNode) selectedNode).myServiceItem);
            }
        }

        return result;
    }
}
