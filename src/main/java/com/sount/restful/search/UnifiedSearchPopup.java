package com.sount.restful.search;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.navigation.action.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public final class UnifiedSearchPopup {

    private static final int SEARCH_DEBOUNCE_MS = 150;
    private static final int POPUP_WIDTH = 960;
    private static final int POPUP_HEIGHT = 480;
    private static final String SELECTED_MODULE_KEY = "GoToRestService.SelectedModule";

    private UnifiedSearchPopup() {
    }

    public static void show(@NotNull Project project, @Nullable String initialText) {
        show(project, initialText, null);
    }

    public static void show(@NotNull Project project, @Nullable String initialText, @Nullable Module currentModule) {
        EndpointIndex index = EndpointIndex.getInstance(project);
        SearchHistory history = SearchHistory.getInstance(project);
        PropertiesComponent props = PropertiesComponent.getInstance(project);

        String initialSearchText = resolveInitialSearchText(initialText, history.getRecentQueries());
        SearchTextField searchField = new SearchTextField(false);
        if (!initialSearchText.isEmpty()) {
            searchField.setText(initialSearchText);
        }

        DefaultListModel<SearchResult> listModel = new DefaultListModel<>();
        UnifiedSearchRenderer renderer = new UnifiedSearchRenderer();
        JBList<SearchResult> resultList = new JBList<>(listModel);
        resultList.setCellRenderer(renderer);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Full-width result list scroll pane
        JScrollPane resultScrollPane = ScrollPaneFactory.createScrollPane(resultList);
        resultScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(JBUI.size(POPUP_WIDTH, POPUP_HEIGHT));

        // Search field at top
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(JBUI.Borders.empty(4));
        JLabel searchLabel = new JLabel(" Search: ");
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Method filter buttons
        JPanel methodFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        methodFilterPanel.setOpaque(false);
        methodFilterPanel.setBorder(JBUI.Borders.empty(0, 4));
        ButtonGroup methodGroup = new ButtonGroup();
        EnumMap<HttpMethod, JToggleButton> methodButtons = new EnumMap<>(HttpMethod.class);

        JToggleButton allBtn = createFilterButton("All", methodGroup, methodFilterPanel);
        allBtn.setSelected(true);
        for (HttpMethod m : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH}) {
            methodButtons.put(m, createFilterButton(m.name(), methodGroup, methodFilterPanel));
        }

        // Module filter dropdown (includes "Current Module: xxx" when available)
        JPanel moduleFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        moduleFilterPanel.setOpaque(false);
        JLabel moduleLabel = new JLabel("Module: ");
        moduleLabel.setFont(moduleLabel.getFont().deriveFont(Font.PLAIN, moduleLabel.getFont().getSize() - 1f));
        moduleFilterPanel.add(moduleLabel);
        JComboBox<String> moduleCombo = new JComboBox<>();
        moduleCombo.setFont(moduleCombo.getFont().deriveFont(Font.PLAIN, moduleCombo.getFont().getSize() - 1f));
        moduleCombo.addItem("All Modules");
        if (currentModule != null) {
            moduleCombo.addItem("Current Module: " + currentModule.getName());
        }
        populateModuleFilter(moduleCombo, index.getItems(), currentModule);
        // Restore persisted selection
        String savedModule = props.getValue(SELECTED_MODULE_KEY);
        if (savedModule != null) {
            for (int i = 0; i < moduleCombo.getItemCount(); i++) {
                if (savedModule.equals(moduleCombo.getItemAt(i))) {
                    moduleCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        moduleFilterPanel.add(moduleCombo);

        JLabel hintLabel = new JLabel("Enter 跳转 · Ctrl+C 复制路径 · Esc 关闭");
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, hintLabel.getFont().getSize() - 2f));
        hintLabel.setForeground(JBColor.GRAY);
        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
        hintPanel.setOpaque(false);
        hintPanel.add(hintLabel);

        // Filter bar: method buttons + module dropdown
        JPanel filterBar = new JPanel(new BorderLayout());
        filterBar.setOpaque(false);
        filterBar.add(methodFilterPanel, BorderLayout.WEST);
        filterBar.add(moduleFilterPanel, BorderLayout.EAST);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(searchPanel, BorderLayout.NORTH);
        topPanel.add(filterBar, BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Full-width results in center
        mainPanel.add(resultScrollPane, BorderLayout.CENTER);

        // Status bar at bottom
        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, statusLabel.getFont().getSize() - 2f));
        statusLabel.setForeground(JBColor.GRAY);

        JButton searchAllModulesBtn = new JButton("搜索全部模块");
        searchAllModulesBtn.setFont(searchAllModulesBtn.getFont().deriveFont(Font.PLAIN, searchAllModulesBtn.getFont().getSize() - 2f));
        searchAllModulesBtn.setMargin(new Insets(2, 8, 2, 8));
        searchAllModulesBtn.setVisible(false);
        searchAllModulesBtn.addActionListener(e -> {
            moduleCombo.setSelectedIndex(0); // "All Modules"
        });

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setOpaque(false);
        statusBar.setBorder(JBUI.Borders.empty(2, 8));
        statusBar.add(statusLabel, BorderLayout.CENTER);
        statusBar.add(searchAllModulesBtn, BorderLayout.EAST);

        // Selection summary bar
        JLabel selectionLabel = new JLabel(" ");
        selectionLabel.setFont(selectionLabel.getFont().deriveFont(Font.PLAIN, selectionLabel.getFont().getSize() - 1f));
        selectionLabel.setForeground(JBColor.GRAY);
        JPanel selectionBar = new JPanel(new BorderLayout());
        selectionBar.setOpaque(false);
        selectionBar.setBorder(JBUI.Borders.empty(2, 8));
        selectionBar.add(selectionLabel, BorderLayout.WEST);
        selectionBar.add(hintPanel, BorderLayout.EAST);

        // Bottom panel combining status and selection
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(statusBar, BorderLayout.NORTH);
        bottomPanel.add(selectionBar, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Search debounce
        Alarm searchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

        // Resolve the active method filter from button group
        Runnable runSearch = () -> {
            String text = searchField.getText();
            HttpMethod methodFilter = resolveSelectedMethod(methodGroup, methodButtons);
            Module filterModule = resolveSelectedModule(moduleCombo, currentModule, index.getItems());
            performSearch(text, index, listModel, resultList, statusLabel, searchAllModulesBtn,
                    filterModule, renderer, methodFilter, history.getSelectedEndpointKey(text),
                    history.getSelectedIndex(text), history.getFirstVisibleIndex(text), history.getScrollY(text));
        };
        Runnable doSearch = () -> {
            history.recordQuery(searchField.getText());
            runSearch.run();
        };

        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(mainPanel, searchField.getTextEditor())
                .setTitle("Search REST Endpoints")
                .setMovable(true)
                .setResizable(true)
                .setRequestFocus(true)
                .setCancelOnWindowDeactivation(true)
                .setMinSize(JBUI.size(600, 350))
                .createPopup();

        Runnable indexListener = () -> SwingUtilities.invokeLater(runSearch);
        index.addListener(indexListener);

        popup.addListener(new JBPopupListener() {
            @Override
            public void beforeShown(@NotNull LightweightWindowEvent event) {
            }

            @Override
            public void onClosed(@NotNull LightweightWindowEvent event) {
                searchAlarm.cancelAllRequests();
                index.removeListener(indexListener);
                recordWindowState(history, searchField.getText(), resultList);
                // Persist selected module
                Object selected = moduleCombo.getSelectedItem();
                props.setValue(SELECTED_MODULE_KEY, selected != null ? selected.toString() : null);
            }
        });

        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                searchAlarm.cancelAllRequests();
                searchAlarm.addRequest(doSearch, SEARCH_DEBOUNCE_MS);
            }
        });

        // Method filter buttons trigger re-search
        for (AbstractButton btn : Collections.list(methodGroup.getElements())) {
            btn.addActionListener(e -> {
                searchAlarm.cancelAllRequests();
                searchAlarm.addRequest(doSearch, 0);
            });
        }

        // Module combo triggers re-search
        moduleCombo.addActionListener(e -> {
            searchAlarm.cancelAllRequests();
            searchAlarm.addRequest(doSearch, 0);
        });

        // Selection listener for summary bar
        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                SearchResult selected = resultList.getSelectedValue();
                if (selected != null) {
                    history.recordSelectedEndpoint(searchField.getText(), selected.item());
                    recordWindowState(history, searchField.getText(), resultList);
                    updateSelectionSummary(selectionLabel, selected.item());
                } else {
                    selectionLabel.setText(" ");
                }
            }
        });

        // Keyboard shortcuts on the result list
        resultList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    navigateToSelected(resultList, popup, history);
                } else if (e.getKeyCode() == KeyEvent.VK_C && e.isControlDown()) {
                    e.consume();
                    copySelectedPath(resultList);
                }
            }
        });

        // Double-click navigates
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelected(resultList, popup, history);
                }
            }
        });

        // Search field keyboard
        installSearchFieldNavigation(searchField, resultList);
        searchField.addKeyboardListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    popup.cancel();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    navigateToSelected(resultList, popup, history);
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    e.consume();
                    moveSelectionFromSearchField(resultList, -1);
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    e.consume();
                    moveSelectionFromSearchField(resultList, 1);
                } else if (e.getKeyCode() == KeyEvent.VK_C && e.isControlDown()) {
                    e.consume();
                    copySelectedPath(resultList);
                }
            }
        });

        Component focusOwner = IdeFocusManager.getInstance(project).getFocusOwner();
        if (focusOwner != null) {
            Window window = SwingUtilities.getWindowAncestor(focusOwner);
            if (window instanceof JFrame frame) {
                popup.showInCenterOf(frame.getRootPane());
            } else {
                popup.showInFocusCenter();
            }
        } else {
            popup.showInFocusCenter();
        }

        // Initial search
        runSearch.run();

        IdeFocusManager.getInstance(project).requestFocus(searchField.getTextEditor(), true);
        searchField.getTextEditor().selectAll();
    }

    // --- Filter helpers ---

    private static JToggleButton createFilterButton(String text, ButtonGroup group, JPanel panel) {
        JToggleButton btn = new JToggleButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (isSelected()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(new Color(0x4A90D9));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
                    g2.dispose();
                }
            }
        };
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, btn.getFont().getSize() - 2f));
        btn.setMargin(new Insets(2, 6, 2, 6));
        btn.setFocusPainted(false);

        btn.addChangeListener(e -> btn.repaint());

        group.add(btn);
        panel.add(btn);
        return btn;
    }

    private static @Nullable HttpMethod resolveSelectedMethod(ButtonGroup group, EnumMap<HttpMethod, JToggleButton> buttons) {
        for (Map.Entry<HttpMethod, JToggleButton> entry : buttons.entrySet()) {
            if (entry.getValue().isSelected()) {
                return entry.getKey();
            }
        }
        return null; // "All" selected
    }

    private static @Nullable Module resolveSelectedModule(@NotNull JComboBox<String> combo,
                                                          @Nullable Module currentModule,
                                                          @NotNull List<RestServiceItem> items) {
        Object selected = combo.getSelectedItem();
        if (selected == null || "All Modules".equals(selected)) return null;
        String selectedStr = selected.toString();
        // "Current Module: xxx" → use currentModule
        if (selectedStr.startsWith("Current Module:") && currentModule != null) {
            return currentModule;
        }
        // Otherwise find by module name
        for (RestServiceItem item : items) {
            if (selectedStr.equals(item.getModuleName())) {
                return item.getModule();
            }
        }
        return null;
    }

    private static void populateModuleFilter(@NotNull JComboBox<String> combo,
                                              @NotNull List<RestServiceItem> items,
                                              @Nullable Module currentModule) {
        Set<String> modules = new LinkedHashSet<>();
        for (RestServiceItem item : items) {
            String name = item.getModuleName();
            if (name != null && !name.isEmpty()) {
                // Skip current module if it's already shown as "Current Module: xxx"
                if (currentModule != null && name.equals(currentModule.getName())) continue;
                modules.add(name);
            }
        }
        List<String> sorted = new ArrayList<>(modules);
        Collections.sort(sorted);
        for (String m : sorted) {
            combo.addItem(m);
        }
    }

    // --- Existing helpers ---

    static @NotNull String resolveInitialSearchText(@Nullable String initialText, @NotNull List<String> recentQueries) {
        if (initialText != null && !initialText.isBlank()) {
            return initialText;
        }
        return recentQueries.isEmpty() ? "" : recentQueries.get(0);
    }

    private static void performSearch(@NotNull String text, @NotNull EndpointIndex index,
                                      @NotNull DefaultListModel<SearchResult> model,
                                      @NotNull JBList<SearchResult> resultList,
                                      @NotNull JLabel statusLabel,
                                      @NotNull JButton searchAllModulesBtn,
                                      @Nullable Module filterModule,
                                      @NotNull UnifiedSearchRenderer renderer,
                                      @Nullable HttpMethod methodFilter,
                                      @Nullable String preferredEndpointKey,
                                      @Nullable Integer preferredSelectionIndex,
                                      @Nullable Integer preferredFirstVisibleIndex,
                                      @Nullable Integer preferredScrollY) {
        List<RestServiceItem> allItems = index.getItems();
        boolean indexReady = index.isReady();
        List<RestServiceItem> searchableItems = allItems;
        if (filterModule != null) {
            List<RestServiceItem> filtered = new java.util.ArrayList<>();
            for (RestServiceItem item : allItems) {
                if (filterModule.equals(item.getModule())) {
                    filtered.add(item);
                }
            }
            searchableItems = filtered;
        }
        final int totalCount = searchableItems.size();
        SearchQuery query = SearchQuery.parse(text);

        // If method filter is set but query didn't parse it, combine
        if (methodFilter != null && query.methodFilter() == null) {
            query = new SearchQuery(query.rawInput(), methodFilter, query.urlPattern(),
                    query.classNamePattern(), query.methodNamePattern(), query.tokens());
        }

        SearchHistory history = SearchHistory.getInstance(index.getProject());
        List<SearchResult> results;

        // Empty query: show recently accessed endpoints (top 20)
        if (text.isEmpty() && methodFilter == null) {
            results = searchableItems.stream()
                    .sorted(Comparator.comparingLong(history::getLastAccessTime).reversed())
                    .limit(20)
                    .map(item -> new SearchResult(item, 0, null))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            results = SearchEngine.search(query, searchableItems, 200, history::getUseCount);
        }

        // Set highlight tokens for renderer
        renderer.setHighlightTokens(query.tokens());

        SwingUtilities.invokeLater(() -> {
            model.clear();
            for (SearchResult result : results) {
                model.addElement(result);
            }
            if (!results.isEmpty()) {
                int selectionIndex = findSelectionIndex(results, preferredEndpointKey, preferredSelectionIndex);
                selectAndRevealIndex(resultList, selectionIndex, preferredFirstVisibleIndex, preferredScrollY);
            }

            statusLabel.setText(buildStatusText(text, results.size(), totalCount, indexReady,
                    filterModule, methodFilter));

            // Show "搜索全部模块" button when no results and a module filter is active
            searchAllModulesBtn.setVisible(results.isEmpty() && filterModule != null && !text.isEmpty());
        });
    }

    static @NotNull String buildStatusText(@NotNull String text, int resultCount, int totalCount, boolean indexReady) {
        return buildStatusText(text, resultCount, totalCount, indexReady, null, null);
    }

    static @NotNull String buildStatusText(@NotNull String text, int resultCount, int totalCount, boolean indexReady,
                                            @Nullable Module filterModule, @Nullable HttpMethod methodFilter) {
        if (!indexReady && resultCount == 0) {
            if (text.isEmpty()) {
                return "Indexing REST endpoints... Results will refresh automatically.";
            }
            return "Indexing REST endpoints for \"" + text + "\"... Results will refresh automatically.";
        }

        if (text.isEmpty()) {
            if (totalCount == 0) {
                return "No endpoints found. Check if project has Spring/JAX-RS controllers and IDE indexing is complete.";
            }
            return totalCount + " endpoints loaded";
        }

        if (resultCount == 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("未找到匹配 \"").append(text).append("\" 的接口");
            if (methodFilter != null) {
                sb.append(" [Method: ").append(methodFilter.name()).append("]");
            }
            if (filterModule != null) {
                sb.append(" · 模块: ").append(filterModule.getName());
            }
            sb.append("。可以尝试: 部分路径(user/login) · 方法名(tryLogin) · 中文描述(用户登录)");
            return sb.toString();
        }

        StringBuilder status = new StringBuilder();
        status.append(resultCount).append(" results found");
        if (methodFilter != null) {
            status.append(" [").append(methodFilter.name()).append("]");
        }
        if (filterModule != null) {
            status.append(" in ").append(filterModule.getName());
        }
        return status.toString();
    }

    static int findSelectionIndex(@NotNull List<SearchResult> results, @Nullable String preferredEndpointKey) {
        return findSelectionIndex(results, preferredEndpointKey, null);
    }

    static int findSelectionIndex(@NotNull List<SearchResult> results,
                                  @Nullable String preferredEndpointKey,
                                  @Nullable Integer preferredSelectionIndex) {
        if (preferredSelectionIndex != null
                && preferredSelectionIndex >= 0
                && preferredSelectionIndex < results.size()) {
            return preferredSelectionIndex;
        }
        if (preferredEndpointKey != null && !preferredEndpointKey.isBlank()) {
            for (int i = 0; i < results.size(); i++) {
                if (preferredEndpointKey.equals(results.get(i).item().getSearchSelectionKey())) {
                    return i;
                }
            }
            for (int i = 0; i < results.size(); i++) {
                if (preferredEndpointKey.equals(results.get(i).item().getEndpointKey())) {
                    return i;
                }
            }
        }
        return results.isEmpty() ? -1 : 0;
    }

    static void selectAndRevealIndex(@NotNull JBList<SearchResult> resultList, int index) {
        selectAndRevealIndex(resultList, index, null, null);
    }

    static void selectAndRevealIndex(@NotNull JBList<SearchResult> resultList,
                                     int index,
                                     @Nullable Integer preferredFirstVisibleIndex) {
        selectAndRevealIndex(resultList, index, preferredFirstVisibleIndex, null);
    }

    static void selectAndRevealIndex(@NotNull JBList<SearchResult> resultList,
                                     int index,
                                     @Nullable Integer preferredFirstVisibleIndex,
                                     @Nullable Integer preferredScrollY) {
        if (index < 0 || index >= resultList.getModel().getSize()) {
            return;
        }
        resultList.setSelectedIndex(index);
        revealIndex(resultList, preferredFirstVisibleIndex, preferredScrollY, index);
        SwingUtilities.invokeLater(() -> {
            if (index >= 0 && index < resultList.getModel().getSize()
                    && resultList.getSelectedIndex() == index) {
                revealIndex(resultList, preferredFirstVisibleIndex, preferredScrollY, index);
            }
        });
    }

    private static void revealIndex(@NotNull JBList<SearchResult> resultList,
                                    @Nullable Integer preferredFirstVisibleIndex,
                                    @Nullable Integer preferredScrollY,
                                    int selectedIndex) {
        if (restoreScrollY(resultList, preferredScrollY)) {
            return;
        }
        int modelSize = resultList.getModel().getSize();
        if (preferredFirstVisibleIndex != null
                && preferredFirstVisibleIndex >= 0
                && preferredFirstVisibleIndex < modelSize) {
            resultList.ensureIndexIsVisible(preferredFirstVisibleIndex);
            return;
        }
        resultList.ensureIndexIsVisible(selectedIndex);
    }

    private static boolean restoreScrollY(@NotNull JBList<SearchResult> resultList,
                                          @Nullable Integer preferredScrollY) {
        if (preferredScrollY == null || preferredScrollY < 0) {
            return false;
        }
        Container parent = resultList.getParent();
        if (!(parent instanceof JViewport viewport)) {
            return false;
        }
        int maxY = Math.max(0, resultList.getPreferredSize().height - viewport.getExtentSize().height);
        int y = Math.min(preferredScrollY, maxY);
        viewport.setViewPosition(new Point(0, y));
        return true;
    }

    private static void recordWindowState(@NotNull SearchHistory history,
                                          @NotNull String query,
                                          @NotNull JBList<SearchResult> resultList) {
        history.recordWindowState(query, resultList.getSelectedIndex(), resultList.getFirstVisibleIndex(),
                resultList.getVisibleRect().y);
    }

    static void moveFocusToResults(@NotNull JBList<SearchResult> resultList) {
        if (resultList.getModel().getSize() <= 0) {
            return;
        }
        int selectedIndex = resultList.getSelectedIndex();
        if (selectedIndex < 0) {
            selectedIndex = 0;
            resultList.setSelectedIndex(selectedIndex);
        }
        resultList.ensureIndexIsVisible(selectedIndex);
        resultList.requestFocusInWindow();
    }

    static void moveSelectionFromSearchField(@NotNull JBList<SearchResult> resultList, int direction) {
        int size = resultList.getModel().getSize();
        if (size <= 0) {
            return;
        }

        int selectedIndex = resultList.getSelectedIndex();
        int nextIndex;
        if (selectedIndex < 0) {
            nextIndex = direction < 0 ? size - 1 : 0;
        } else {
            nextIndex = Math.max(0, Math.min(size - 1, selectedIndex + direction));
        }
        resultList.setSelectedIndex(nextIndex);
        resultList.ensureIndexIsVisible(nextIndex);
    }

    private static void installSearchFieldNavigation(@NotNull SearchTextField searchField,
                                                     @NotNull JBList<SearchResult> resultList) {
        JComponent editor = searchField.getTextEditor();
        InputMap inputMap = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = editor.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "restful.search.selectPrevious");
        actionMap.put("restful.search.selectPrevious", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                moveSelectionFromSearchField(resultList, -1);
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "restful.search.selectNext");
        actionMap.put("restful.search.selectNext", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                moveSelectionFromSearchField(resultList, 1);
            }
        });
    }

    private static void navigateToSelected(@NotNull JBList<SearchResult> resultList,
                                           @NotNull JBPopup popup,
                                           @NotNull SearchHistory history) {
        SearchResult selected = resultList.getSelectedValue();
        if (selected != null) {
            RestServiceItem item = selected.item();
            history.recordAccess(item);
            popup.closeOk(null);
            item.navigate(true);
        }
    }

    private static void copySelectedPath(@NotNull JBList<SearchResult> resultList) {
        SearchResult selected = resultList.getSelectedValue();
        if (selected != null) {
            String path = selected.item().getUrl();
            if (path != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(path), null);
            }
        }
    }

    private static void updateSelectionSummary(@NotNull JLabel selectionLabel, @NotNull RestServiceItem item) {
        String method = item.getMethod() != null ? item.getMethod().name() : "?";
        String url = item.getUrl() != null ? item.getUrl() : "";
        String controllerName = item.getControllerName();
        String methodName = item.getMethodName();
        String moduleName = item.getModuleName();

        StringBuilder sb = new StringBuilder();
        sb.append("当前选中：").append(method).append(" ").append(url);
        if (controllerName != null && !controllerName.isEmpty() && methodName != null && !methodName.isEmpty()) {
            sb.append(" · ").append(controllerName).append("#").append(methodName);
        }
        if (moduleName != null && !moduleName.isEmpty()) {
            sb.append(" · ").append(moduleName);
        }
        selectionLabel.setText(sb.toString());
    }
}
