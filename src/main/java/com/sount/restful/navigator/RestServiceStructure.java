package com.sount.restful.navigator;


import com.intellij.icons.AllIcons;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.AppExecutorUtil;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
    private int totalServiceCount = 0;
    private final AtomicLong detailRequestSequence = new AtomicLong();
    private List<RestServiceProject> allProjects = Collections.emptyList();
    private String filterText = "";

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
                if (nodeClass.isInstance(node)) {
                    filtered.add(nodeClass.cast(node));
                }
            }
        }
        return filtered;
    }

    public void update() {
        RestServiceProjectsManager.getInstance(myProject).getServiceProjects(this::updateProjects);
    }

    public void updateProjects(List<RestServiceProject> projects) {
        allProjects = new ArrayList<>(projects);
        totalServiceCount = allProjects.stream().mapToInt(project -> project.serviceItems.size()).sum();
        rebuildTree();
    }

    public void setFilterText(String filterText) {
        this.filterText = filterText == null ? "" : filterText.trim();
        rebuildTree();
    }

    private void rebuildTree() {
        serviceCount = 0;

        myProjectToNodeMapping.clear();
        myRoot.projectNodes.clear();

        for (RestServiceProject each : allProjects) {
            ProjectNode node = new ProjectNode(myRoot, each, filterServices(each.serviceItems));
            if (node.serviceNodes.isEmpty()) {
                continue;
            }
            serviceCount += node.serviceNodes.size();
            myRoot.projectNodes.add(node);
            myProjectToNodeMapping.put(each, node);
        }

        if (myTree.getSelectionPath() == null) {
            resetRestServiceDetail();
        }

        myTreeModel.reload();
        expandRoot();
    }

    private List<RestServiceItem> filterServices(List<RestServiceItem> serviceItems) {
        if (filterText.isBlank()) {
            return serviceItems;
        }

        List<RestServiceItem> filtered = new ArrayList<>();
        for (RestServiceItem serviceItem : serviceItems) {
            if (serviceItem.matches(filterText)) {
                filtered.add(serviceItem);
            }
        }
        return filtered;
    }

    private void resetRestServiceDetail() {
        myRestServiceDetail.resetRequestTabbedPane();
        myRestServiceDetail.setEndpointSummary("Select an endpoint to inspect and test");
        myRestServiceDetail.setSourceValue(filterText.isBlank()
                ? "Search by URL, method, module, or source"
                : "Filter: " + filterText);
        myRestServiceDetail.setBodyTypeValue("NONE");
        myRestServiceDetail.setMethodValue(HttpMethod.GET.name());
        myRestServiceDetail.setUrlValue("URL");
        myRestServiceDetail.clearResponseMeta();
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
            if (totalServiceCount == 0) {
                return "No services";
            }
            if (filterText.isBlank()) {
                return String.format("Found %d services", totalServiceCount);
            }
            return String.format("Showing %d of %d services", serviceCount, totalServiceCount);
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

        public ProjectNode(BaseSimpleNode parent, RestServiceProject project, List<RestServiceItem> visibleItems) {
            super(project.getModuleName(), true);
            myProject = project;
            setNodeIcon(ToolkitIcons.MODULE);
            updateServiceNodes(visibleItems);
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
            return myProject.getModuleName() + " (" + serviceNodes.size() + ")";
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
            long requestId = detailRequestSequence.incrementAndGet();
            myRestServiceDetail.resetRequestTabbedPane();

            ReadAction.nonBlocking(() -> buildServiceDetailState(myServiceItem))
                    .inSmartMode(myProject)
                    .expireWith(myProject)
                    .finishOnUiThread(ModalityState.defaultModalityState(), detailState -> {
                        if (requestId != detailRequestSequence.get()) {
                            return;
                        }
                        showServiceDetail(detailState);
                    })
                    .submit(AppExecutorUtil.getAppExecutorService());
        }

        private ServiceDetailState buildServiceDetailState(RestServiceItem serviceItem) {
            String method = serviceItem.getMethod() != null ? String.valueOf(serviceItem.getMethod()) : HttpMethod.GET.name();
            String fullUrl = serviceItem.getFullUrl();
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

            return new ServiceDetailState(method, fullUrl, serviceItem.getName(),
                    serviceItem.getLocationText(), requestParams, requestBodyJson);
        }

        private void showServiceDetail(ServiceDetailState detailState) {
            myRestServiceDetail.resetRequestTabbedPane();
            myRestServiceDetail.setEndpointSummary(detailState.method() + " " + detailState.displayName());
            myRestServiceDetail.setSourceValue(detailState.sourceLocation());
            myRestServiceDetail.setMethodValue(detailState.method());
            myRestServiceDetail.setUrlValue(detailState.fullUrl());
            myRestServiceDetail.setBodyTypeValue(StringUtils.isNotBlank(detailState.requestBodyJson()) ? "JSON" : "NONE");
            myRestServiceDetail.clearResponseMeta();
            myRestServiceDetail.initTab();
            myRestServiceDetail.addRequestParamsTab(detailState.requestParams());

            if (StringUtils.isNotBlank(detailState.requestBodyJson())) {
                myRestServiceDetail.addRequestBodyTabPanel(detailState.requestBodyJson());
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
                return;
            }

            if ((psiElement.getLanguage() == JavaLanguage.INSTANCE
                    || (psiElement.getLanguage() == KotlinLanguage.INSTANCE && psiElement instanceof KtNamedFunction))
                    && serviceItem.canNavigate()) {
                serviceItem.navigate(true);
            }
        }

        @Override
        @Nullable
        @NonNls
        protected String getMenuId() {
            return "Toolkit.NavigatorServiceMenu";
        }
    }

    private record ServiceDetailState(String method,
                                      String fullUrl,
                                      String displayName,
                                      String sourceLocation,
                                      String requestParams,
                                      String requestBodyJson) {
    }
}
