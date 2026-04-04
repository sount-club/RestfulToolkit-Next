package com.sount.restful.navigator;


import com.intellij.icons.AllIcons;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.OpenSourceUtil;
import com.sount.restful.common.KtFunctionHelper;
import com.sount.restful.common.PsiMethodHelper;
import com.sount.restful.common.ToolkitIcons;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.navigation.action.RestServiceItem;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RestServiceStructure {
    public static final Logger LOG = Logger.getInstance(RestServiceStructure.class);
    private final Project myProject;
    private final RestServiceProjectsManager myProjectsManager;
    private final Map<RestServiceProject, ProjectNode> myProjectToNodeMapping = new HashMap<>();
    RestServiceDetail myRestServiceDetail;
    private JTree myTree;
    private DefaultTreeModel myTreeModel;
    private RootNode myRoot;
    private int serviceCount = 0;

    public RestServiceStructure(Project project,
                                RestServiceProjectsManager projectsManager,
                                JTree tree) {
        myProject = project;
        myProjectsManager = projectsManager;
        myTree = tree;
        myRestServiceDetail = project.getService(RestServiceDetail.class);

        myRoot = new RootNode();
        myTreeModel = new DefaultTreeModel(myRoot);

        configureTree(tree);
        tree.setModel(myTreeModel);

        setupTreeSelectionListener(tree);
        expandRoot();
    }

    private void configureTree(JTree tree) {
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
    }

    private void expandRoot() {
        if (myTree.getModel() != null) {
            TreePath path = new TreePath(myRoot.getPath());
            myTree.expandPath(path);
        }
    }

    private void setupTreeSelectionListener(JTree tree) {
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    int row = tree.getLeadSelectionRow();
                    if (row >= 0) {
                        TreePath path = tree.getPathForRow(row);
                        if (path != null) {
                            Object lastPathComponent = path.getLastPathComponent();
                            if (lastPathComponent instanceof BaseSimpleNode) {
                                ((BaseSimpleNode) lastPathComponent).handleSelection(tree);
                            }
                        }
                    }
                } else if (e.getClickCount() == 2) {
                    int row = tree.getLeadSelectionRow();
                    if (row >= 0) {
                        TreePath path = tree.getPathForRow(row);
                        if (path != null) {
                            Object lastPathComponent = path.getLastPathComponent();
                            if (lastPathComponent instanceof ServiceNode) {
                                ((ServiceNode) lastPathComponent).handleDoubleClick(tree, e);
                            }
                        }
                    }
                }
            }
        });
    }

    public static <T extends BaseSimpleNode> List<T> getSelectedNodes(JTree tree, Class<T> nodeClass) {
        final List<T> filtered = new ArrayList<>();
        TreePath[] treePaths = tree.getSelectionPaths();
        if (treePaths != null) {
            for (TreePath treePath : treePaths) {
                Object node = treePath.getLastPathComponent();
                if (nodeClass == null || nodeClass.isInstance(node)) {
                    //noinspection unchecked
                    filtered.add((T) node);
                }
            }
        }
        return filtered;
    }

    public void update() {
        List<RestServiceProject> projects = RestServiceProjectsManager.getInstance(myProject).getServiceProjects();
        updateProjects(projects);
    }

    public void updateProjects(List<RestServiceProject> projects) {
        serviceCount = 0;

        myProjectToNodeMapping.clear();
        myRoot.projectNodes.clear();

        for (RestServiceProject each : projects) {
            serviceCount += each.serviceItems.size();
            ProjectNode node = new ProjectNode(myRoot, each);
            myRoot.projectNodes.add(node);
            myProjectToNodeMapping.put(each, node);
        }

        myTreeModel.nodeChanged(myRoot);
        for (int i = 0; i < myRoot.getChildCount(); i++) {
            myTreeModel.nodeChanged(myRoot.getChildAt(i));
        }
    }

    private void resetRestServiceDetail() {
        myRestServiceDetail.resetRequestTabbedPane();
        myRestServiceDetail.setMethodValue(HttpMethod.GET.name());
        myRestServiceDetail.setUrlValue("URL");
        myRestServiceDetail.initTab();
    }

    public abstract class BaseSimpleNode extends DefaultMutableTreeNode {

        protected Icon myIcon;

        protected BaseSimpleNode() {
            super();
        }

        protected BaseSimpleNode(Object userObject) {
            super(userObject);
        }

        protected BaseSimpleNode(Object userObject, boolean allowsChildren) {
            super(userObject, allowsChildren);
        }

        public void setNodeIcon(Icon icon) {
            this.myIcon = icon;
        }

        @Nullable
        @NonNls
        String getActionId() {
            return null;
        }

        @Nullable
        @NonNls
        String getMenuId() {
            return null;
        }

        @Nullable
        public String getName() {
            return null;
        }

        @Nullable
        public Icon getIcon() {
            return myIcon;
        }

        public void handleSelection(JTree tree) {
        }

        public void handleDoubleClick(JTree tree, InputEvent inputEvent) {
        }

        protected void rebuildChildren() {
            if (myTreeModel != null) {
                myTreeModel.nodeStructureChanged(this);
            }
        }

        protected void refreshNode() {
            if (myTreeModel != null) {
                int index = getParent() != null ? getParent().getIndex(this) : -1;
                if (index >= 0) {
                    myTreeModel.nodeChanged(this);
                }
            }
        }
    }

    public class RootNode extends BaseSimpleNode {
        List<ProjectNode> projectNodes = new ArrayList<>();

        protected RootNode() {
            super("REST Services", true);
            setNodeIcon(AllIcons.Actions.ModuleDirectory);
        }

        @Override
        public String getName() {
            String s = "Found %d services ";
            return serviceCount > 0 ? String.format(s, serviceCount) : "No services";
        }

        @Override
        public Icon getIcon() {
            return myIcon;
        }

        @Override
        public int getChildCount() {
            return projectNodes.size();
        }

        @Override
        public ProjectNode getChildAt(int index) {
            return projectNodes.get(index);
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public void handleSelection(JTree tree) {
            resetRestServiceDetail();
        }
    }

    public class ProjectNode extends BaseSimpleNode {
        List<ServiceNode> serviceNodes = new ArrayList<>();
        RestServiceProject myProject;

        public ProjectNode(BaseSimpleNode parent, RestServiceProject project) {
            super(project.getModuleName(), true);
            myProject = project;
            setNodeIcon(ToolkitIcons.MODULE);
            updateServiceNodes(project.serviceItems);
        }

        private void updateServiceNodes(List<RestServiceItem> serviceItems) {
            serviceNodes.clear();
            for (RestServiceItem serviceItem : serviceItems) {
                ServiceNode serviceNode = new ServiceNode(this, serviceItem);
                serviceNodes.add(serviceNode);
            }
            rebuildChildren();
        }

        @Override
        public String getName() {
            return myProject.getModuleName();
        }

        @Override
        public Icon getIcon() {
            return myIcon;
        }

        @Override
        public int getChildCount() {
            return serviceNodes.size();
        }

        @Override
        public ServiceNode getChildAt(int index) {
            return serviceNodes.get(index);
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        @Nullable
        @NonNls
        protected String getActionId() {
            return "Toolkit.RefreshServices";
        }

        @Override
        public void handleSelection(JTree tree) {
            resetRestServiceDetail();
        }
    }

    public class ServiceNode extends BaseSimpleNode {
        RestServiceItem myServiceItem;

        public ServiceNode(BaseSimpleNode parent, RestServiceItem serviceItem) {
            super(serviceItem.getName(), false);
            myServiceItem = serviceItem;
            setNodeIcon(ToolkitIcons.METHOD.get(serviceItem.getMethod()));
        }

        @Override
        public String getName() {
            return myServiceItem.getName();
        }

        @Override
        public Icon getIcon() {
            return myIcon;
        }

        @Override
        public int getChildCount() {
            return 0;
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public void handleSelection(JTree tree) {
            showServiceDetail(myServiceItem);
        }

        private void showServiceDetail(RestServiceItem serviceItem) {
            myRestServiceDetail.resetRequestTabbedPane();

            String method = serviceItem.getMethod() != null ? String.valueOf(serviceItem.getMethod()) : HttpMethod.GET.name();
            myRestServiceDetail.setMethodValue(method);
            myRestServiceDetail.setUrlValue(serviceItem.getFullUrl());

            String requestParams = "";
            String requestBodyJson = "";
            PsiElement psiElement = serviceItem.getPsiElement();
            if (psiElement.getLanguage() == JavaLanguage.INSTANCE) {
                PsiMethodHelper psiMethodHelper = PsiMethodHelper.create(serviceItem.getPsiMethod()).withModule(serviceItem.getModule());
                requestParams = psiMethodHelper.buildParamString();
                requestBodyJson = psiMethodHelper.buildRequestBodyJson();

            } else if (psiElement.getLanguage() == KotlinLanguage.INSTANCE) {
                if (psiElement instanceof KtNamedFunction) {
                    KtNamedFunction ktNamedFunction = (KtNamedFunction) psiElement;
                    KtFunctionHelper ktFunctionHelper = KtFunctionHelper.create(ktNamedFunction).withModule(serviceItem.getModule());
                    requestParams = ktFunctionHelper.buildParamString();
                    requestBodyJson = ktFunctionHelper.buildRequestBodyJson();
                }

            }

            myRestServiceDetail.addRequestParamsTab(requestParams);

            if (StringUtils.isNotBlank(requestBodyJson)) {
                myRestServiceDetail.addRequestBodyTabPanel(requestBodyJson);
            }
        }

        @Override
        public void handleDoubleClick(JTree tree, InputEvent inputEvent) {
            RestServiceItem serviceItem = myServiceItem;
            PsiElement psiElement = serviceItem.getPsiElement();

            if (!psiElement.isValid()) {
                LOG.info("psiMethod is invalid: ");
                LOG.info(psiElement.toString());
                RestServicesNavigator.getInstance(serviceItem.getModule().getProject()).scheduleStructureUpdate();
            }

            if (psiElement.getLanguage() == JavaLanguage.INSTANCE) {
                PsiMethod psiMethod = serviceItem.getPsiMethod();
                OpenSourceUtil.navigate(psiMethod);

            } else if (psiElement.getLanguage() == KotlinLanguage.INSTANCE) {
                if (psiElement instanceof KtNamedFunction) {
                    KtNamedFunction ktNamedFunction = (KtNamedFunction) psiElement;
                    OpenSourceUtil.navigate(ktNamedFunction);
                }
            }
        }

        @Override
        @Nullable
        @NonNls
        protected String getMenuId() {
            return "Toolkit.NavigatorServiceMenu";
        }
    }
}
