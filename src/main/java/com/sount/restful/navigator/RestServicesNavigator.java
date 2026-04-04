package com.sount.restful.navigator;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.sount.restful.common.ToolkitIcons;
import com.sount.utils.RestfulToolkitBundle;
import com.sount.utils.ToolkitUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;

@State(name = "RestServicesNavigator", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class RestServicesNavigator implements PersistentStateComponent<RestServicesNavigatorState> {

    public static final Logger LOG = Logger.getInstance(RestServicesNavigator.class);

    public static final String TOOL_WINDOW_ID = "RestServices";

    protected final Project myProject;

    protected RestServiceStructure myStructure;

    RestServicesNavigatorState myState = new RestServicesNavigatorState();

    private JTree myTree;

    private ToolWindow myToolWindow;

    private final RestServiceProjectsManager myProjectsManager;

    public RestServicesNavigator(Project project) {
        myProject = project;
        myProjectsManager = RestServiceProjectsManager.getInstance(project);
    }

    public static RestServicesNavigator getInstance(Project p) {
        return p.getService(RestServicesNavigator.class);
    }

    private void initTree() {
        myTree = new JTree() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                final JLabel myLabel = new JLabel(RestfulToolkitBundle.message("toolkit.navigator.nothing.to.display",
                        ToolkitUtil.formatHtmlImage(null)));

                if (!myProject.isInitialized()) {
                    myLabel.setFont(getFont());
                    myLabel.setBackground(getBackground());
                    myLabel.setForeground(getForeground());
                    Rectangle bounds = getBounds();
                    Dimension size = myLabel.getPreferredSize();
                    myLabel.setBounds(0, 0, size.width, size.height);

                    int x = (bounds.width - size.width) / 2;
                    Graphics g2 = g.create(bounds.x + x, bounds.y + 20, bounds.width, bounds.height);
                    try {
                        myLabel.paint(g2);
                    } finally {
                        g2.dispose();
                    }
                }
            }
        };

        myTree.setRootVisible(true);
        myTree.setShowsRootHandles(true);
        myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        myTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                Component comp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                if (value instanceof RestServiceStructure.BaseSimpleNode node) {
                    setText(node.getName());
                    Icon icon = node.getIcon();
                    if (icon != null) {
                        setIcon(icon);
                    }
                }
                return comp;
            }
        });
    }

    public void initToolWindow() {
        final ToolWindowManager manager = ToolWindowManager.getInstance(myProject);
        ToolWindow toolWindow = manager.getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow == null) {
            return;
        }

        bindToolWindow(toolWindow);
        myToolWindow.setAvailable(true);
        myToolWindow.show(this::scheduleStructureUpdate);
    }

    public void bindToolWindow(@NotNull ToolWindow toolWindow) {
        myToolWindow = toolWindow;
        myToolWindow.setIcon(ToolkitIcons.SERVICE);

        if (myTree == null) {
            initTree();
        }

        ContentManager contentManager = myToolWindow.getContentManager();
        if (contentManager.getContentCount() > 0) {
            return;
        }

        JPanel panel = new RestServicesNavigatorPanel(myProject, myTree);
        final ContentFactory contentFactory = ContentFactory.getInstance();
        final Content content = contentFactory.createContent(panel, "", false);
        contentManager.addContent(content);
        contentManager.setSelectedContent(content, false);
    }

    boolean wasVisible = false;

    public void stateChanged() {
        if (myToolWindow == null) {
            return;
        }
        if (myToolWindow.isDisposed()) {
            return;
        }
        boolean visible = myToolWindow.isVisible();
        if (!visible || wasVisible) {
            return;
        }
        scheduleStructureUpdate();
        wasVisible = true;
    }

    public void scheduleStructureUpdate() {
        scheduleStructureRequest(() -> myStructure.update());
    }

    private void scheduleStructureRequest(final Runnable r) {
        if (myToolWindow == null) {
            return;
        }
        ToolkitUtil.runWhenProjectIsReady(myProject, () -> {
            if (!myToolWindow.isVisible()) {
                return;
            }

            boolean shouldCreate = myStructure == null;
            if (shouldCreate) {
                initStructure();
            }

            r.run();
        });
    }

    private void initStructure() {
        myStructure = new RestServiceStructure(myProject, myProjectsManager, myTree);
    }

    private void listenForProjectsChanges() {
    }

    @Nullable
    @Override
    public RestServicesNavigatorState getState() {
        if (myStructure != null) {
            try {
                myState.treeState = new Element("root");
            } catch (Exception e) {
                LOG.warn(e);
            }
        }
        return myState;
    }

    @Override
    public void loadState(@NotNull RestServicesNavigatorState state) {
        myState = state;
        scheduleStructureUpdate();
    }
}
