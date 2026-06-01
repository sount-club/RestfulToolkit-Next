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
import com.intellij.util.ui.UIUtil;
import com.sount.restful.navigation.action.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.util.List;

public final class UnifiedSearchPopup {

    private static final int SEARCH_DEBOUNCE_MS = 150;
    private static final int POPUP_WIDTH = 900;
    private static final int POPUP_HEIGHT = 450;
    private static final int RESULT_LIST_WIDTH = 520;
    private static final int INITIAL_DIVIDER_LOCATION = 560;
    private static final String ONLY_CURRENT_MODULE_KEY = "GoToRestService.OnlyCurrentModule";

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

        // Preview panel on the right
        UnifiedSearchPreview preview = new UnifiedSearchPreview(project);
        preview.setPreferredSize(JBUI.size(280, 0));

        // Keep preview bounded on the right so resizing the popup does not create sideways dragging.
        JSplitPane resultsAndPreviewPanel = createResultsAndPreviewPanel(createResultListScrollPane(resultList), preview);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(JBUI.size(POPUP_WIDTH, POPUP_HEIGHT));

        // Search field at top
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(JBUI.Borders.empty(4));
        JLabel searchLabel = new JLabel(" Search: ");
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // "Only This Module" checkbox
        JCheckBox onlyModuleCheckbox = new JCheckBox("Only This Module");
        onlyModuleCheckbox.setFont(onlyModuleCheckbox.getFont().deriveFont(Font.PLAIN, onlyModuleCheckbox.getFont().getSize() - 1f));
        boolean moduleAvailable = currentModule != null;
        onlyModuleCheckbox.setEnabled(moduleAvailable);
        onlyModuleCheckbox.setSelected(moduleAvailable && props.isTrueValue(ONLY_CURRENT_MODULE_KEY));

        JLabel hintLabel = new JLabel("Enter: navigate  |  Esc: close  |  Ctrl+D: favorite");
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, hintLabel.getFont().getSize() - 2f));
        hintLabel.setForeground(JBColor.GRAY);
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        bottomBar.add(onlyModuleCheckbox, BorderLayout.WEST);
        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
        hintPanel.setOpaque(false);
        hintPanel.add(hintLabel);
        bottomBar.add(hintPanel, BorderLayout.EAST);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(searchPanel, BorderLayout.CENTER);
        topPanel.add(bottomBar, BorderLayout.SOUTH);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Results and preview in center
        mainPanel.add(resultsAndPreviewPanel, BorderLayout.CENTER);

        // Status bar at bottom
        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, statusLabel.getFont().getSize() - 2f));
        statusLabel.setForeground(JBColor.GRAY);
        statusLabel.setBorder(JBUI.Borders.empty(2, 8));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        // Search debounce
        Alarm searchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

        Runnable runSearch = () -> {
            String text = searchField.getText();
            Module filterModule = (currentModule != null && onlyModuleCheckbox.isSelected()) ? currentModule : null;
            performSearch(text, index, listModel, resultList, statusLabel, filterModule,
                    renderer, history.getSelectedEndpointKey(text),
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

        preview.setOnNavigate(() -> popup.closeOk(null));

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
                // Persist checkbox state
                props.setValue(ONLY_CURRENT_MODULE_KEY, onlyModuleCheckbox.isSelected());
            }
        });

        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                searchAlarm.cancelAllRequests();
                searchAlarm.addRequest(doSearch, SEARCH_DEBOUNCE_MS);
            }
        });

        // Checkbox change triggers re-search
        onlyModuleCheckbox.addActionListener(e -> {
            props.setValue(ONLY_CURRENT_MODULE_KEY, onlyModuleCheckbox.isSelected());
            searchAlarm.cancelAllRequests();
            searchAlarm.addRequest(doSearch, 0);
        });

        // Selection listener for preview
        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                SearchResult selected = resultList.getSelectedValue();
                if (selected != null) {
                    history.recordSelectedEndpoint(searchField.getText(), selected.item());
                    recordWindowState(history, searchField.getText(), resultList);
                    preview.showPreview(selected.item());
                } else {
                    preview.clearPreview();
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
                } else if (e.getKeyCode() == KeyEvent.VK_D && e.isControlDown()) {
                    e.consume();
                    toggleFavorite(resultList, history);
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
                } else if (e.getKeyCode() == KeyEvent.VK_D && e.isControlDown()) {
                    e.consume();
                    toggleFavorite(resultList, history);
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
        SwingUtilities.invokeLater(() -> resultsAndPreviewPanel.setDividerLocation(JBUI.scale(INITIAL_DIVIDER_LOCATION)));

        // Initial search
        runSearch.run();

        IdeFocusManager.getInstance(project).requestFocus(searchField.getTextEditor(), true);
        searchField.getTextEditor().selectAll();
    }

    static @NotNull String resolveInitialSearchText(@Nullable String initialText, @NotNull List<String> recentQueries) {
        if (initialText != null && !initialText.isBlank()) {
            return initialText;
        }
        return recentQueries.isEmpty() ? "" : recentQueries.get(0);
    }

    static @NotNull JScrollPane createResultListScrollPane(@NotNull JBList<SearchResult> resultList) {
        resultList.setFixedCellWidth(JBUI.scale(RESULT_LIST_WIDTH));
        JScrollPane resultScrollPane = ScrollPaneFactory.createScrollPane(resultList);
        resultScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        resultScrollPane.setPreferredSize(JBUI.size(RESULT_LIST_WIDTH, 0));
        resultScrollPane.setMinimumSize(JBUI.emptySize());
        return resultScrollPane;
    }

    static @NotNull JSplitPane createResultsAndPreviewPanel(@NotNull JScrollPane resultScrollPane,
                                                            @NotNull JComponent preview) {
        resultScrollPane.setMinimumSize(JBUI.emptySize());
        preview.setMinimumSize(JBUI.emptySize());
        preview.setMaximumSize(new Dimension(UnifiedSearchPreview.MAX_PREVIEW_WIDTH, Integer.MAX_VALUE));

        JSplitPane splitPane = new BoundedPreviewSplitPane(resultScrollPane, preview,
                UnifiedSearchPreview.MAX_PREVIEW_WIDTH);
        splitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this);
                divider.setBorder(JBUI.Borders.customLine(JBColor.border()));
                divider.setBackground(UIUtil.getPanelBackground());
                return divider;
            }
        });
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(1.0);
        splitPane.setBorder(JBUI.Borders.empty());
        splitPane.setDividerLocation(JBUI.scale(INITIAL_DIVIDER_LOCATION));
        splitPane.setMinimumSize(JBUI.emptySize());
        return splitPane;
    }

    private static final class BoundedPreviewSplitPane extends JSplitPane {
        private final int maxPreviewWidth;
        private boolean adjustingDivider;

        private BoundedPreviewSplitPane(@NotNull JComponent resultScrollPane,
                                        @NotNull JComponent preview,
                                        int maxPreviewWidth) {
            super(JSplitPane.HORIZONTAL_SPLIT, resultScrollPane, preview);
            this.maxPreviewWidth = maxPreviewWidth;
            addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, this::clampDividerLocation);
        }

        @Override
        public void doLayout() {
            super.doLayout();
            clampDividerLocation(null);
        }

        private void clampDividerLocation(@Nullable PropertyChangeEvent event) {
            if (adjustingDivider) {
                return;
            }
            int boundedLocation = boundedDividerLocation(getDividerLocation());
            if (boundedLocation == getDividerLocation()) {
                return;
            }
            adjustingDivider = true;
            try {
                setDividerLocation(boundedLocation);
            } finally {
                adjustingDivider = false;
            }
        }

        private int boundedDividerLocation(int dividerLocation) {
            int width = getWidth();
            if (width <= 0) {
                return dividerLocation;
            }
            int minimumLocation = Math.max(0, width - getDividerSize() - maxPreviewWidth);
            return Math.max(dividerLocation, minimumLocation);
        }
    }

    private static void performSearch(@NotNull String text, @NotNull EndpointIndex index,
                                      @NotNull DefaultListModel<SearchResult> model,
                                      @NotNull JBList<SearchResult> resultList,
                                      @NotNull JLabel statusLabel,
                                      @Nullable Module filterModule,
                                      @NotNull UnifiedSearchRenderer renderer) {
        performSearch(text, index, model, resultList, statusLabel, filterModule, renderer, null, null, null, null);
    }

    private static void performSearch(@NotNull String text, @NotNull EndpointIndex index,
                                      @NotNull DefaultListModel<SearchResult> model,
                                      @NotNull JBList<SearchResult> resultList,
                                      @NotNull JLabel statusLabel,
                                      @Nullable Module filterModule,
                                      @NotNull UnifiedSearchRenderer renderer,
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
        List<SearchResult> results = SearchEngine.search(query, searchableItems);

        // Extract highlight pattern from query
        String highlightPattern = null;
        if (query.urlPattern() != null) {
            highlightPattern = query.urlPattern();
        } else if (query.rawInput() != null && !query.rawInput().isEmpty()) {
            highlightPattern = query.rawInput();
        }
        final String finalPattern = highlightPattern;
        renderer.setHighlightPattern(finalPattern);

        SwingUtilities.invokeLater(() -> {
            model.clear();
            for (SearchResult result : results) {
                model.addElement(result);
            }
            if (!results.isEmpty()) {
                int selectionIndex = findSelectionIndex(results, preferredEndpointKey, preferredSelectionIndex);
                selectAndRevealIndex(resultList, selectionIndex, preferredFirstVisibleIndex, preferredScrollY);
            }

            statusLabel.setText(buildStatusText(text, results.size(), totalCount, indexReady));
        });
    }

    static @NotNull String buildStatusText(@NotNull String text, int resultCount, int totalCount, boolean indexReady) {
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
            String statusText = "No results found for \"" + text + "\"";
            if (totalCount > 0) {
                statusText += " (" + totalCount + " endpoints indexed)";
            } else {
                statusText += ". Index may be loading, please wait...";
            }
            return statusText;
        }

        return resultCount + " results found";
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

    private static void toggleFavorite(@NotNull JBList<SearchResult> resultList,
                                       @NotNull SearchHistory history) {
        SearchResult selected = resultList.getSelectedValue();
        if (selected != null) {
            history.toggleFavorite(selected.item());
            resultList.repaint();
        }
    }
}
