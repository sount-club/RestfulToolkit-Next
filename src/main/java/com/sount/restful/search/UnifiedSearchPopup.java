package com.sount.restful.search;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
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
import com.sount.restful.navigation.RestServiceItem;
import com.sount.restful.utils.RestfulToolkitBundle;
import com.sount.restful.utils.RestfulToolkitBundle.Keys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Entry point for the unified REST endpoint search popup.
 * Delegates search logic to {@link SearchController} and
 * user interaction actions to {@link SearchPopupActions}.
 */
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
        index.ensureRebuildScheduled();
        SearchHistory history = SearchHistory.getInstance(project);
        PropertiesComponent props = PropertiesComponent.getInstance(project);

        String initialSearchText = SearchPopupModel.resolveInitialSearchText(initialText, history.getRecentQueries());
        SearchTextField searchField = new SearchTextField(false);
        if (!initialSearchText.isEmpty()) {
            searchField.setText(initialSearchText);
        }

        DefaultListModel<SearchResult> listModel = new DefaultListModel<>();
        UnifiedSearchRenderer renderer = new UnifiedSearchRenderer();
        JBList<SearchResult> resultList = new JBList<>(listModel);
        resultList.setCellRenderer(renderer);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane resultScrollPane = ScrollPaneFactory.createScrollPane(resultList);
        resultScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        PopupComponents components = buildMainPanel(searchField, resultList, resultScrollPane,
                index, currentModule, props);

        Alarm searchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
        SearchController.UpdateGuard updateGuard = new SearchController.UpdateGuard();
        AtomicBoolean navigateWhenResultsArrive = new AtomicBoolean(false);
        AtomicReference<JBPopup> popupRef = new AtomicReference<>();

        Runnable navigatePendingSelection = () -> {
            if (!navigateWhenResultsArrive.get()) {
                return;
            }
            JBPopup activePopup = popupRef.get();
            if (activePopup != null && resultList.getSelectedValue() != null) {
                navigateWhenResultsArrive.set(false);
                SearchPopupActions.navigateToSelected(resultList, activePopup, history,
                        index, components.statusLabel);
            } else if (index.isReady()) {
                navigateWhenResultsArrive.set(false);
            }
        };

        Runnable runSearch = () -> {
            String text = searchField.getText();
            HttpMethod methodFilter = SearchController.resolveSelectedMethod(
                    components.methodGroup, components.methodButtons);
            Module filterModule = SearchController.resolveSelectedModule(
                    components.moduleCombo, currentModule, index.getItems());
            SearchController.performSearch(text, index, listModel, resultList,
                    components.statusLabel, components.searchAllModulesBtn,
                    filterModule, renderer, updateGuard, methodFilter,
                    history.getSelectedEndpointKey(text),
                    history.getSelectedIndex(text),
                    history.getFirstVisibleIndex(text),
                    history.getScrollY(text), navigatePendingSelection);
        };
        Runnable doSearch = () -> {
            history.recordQuery(searchField.getText());
            runSearch.run();
        };
        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(components.mainPanel, searchField.getTextEditor())
                .setTitle(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_TITLE))
                .setMovable(true)
                .setResizable(true)
                .setRequestFocus(true)
                .setCancelOnWindowDeactivation(true)
                .setMinSize(JBUI.size(600, 350))
                .createPopup();
        popupRef.set(popup);
        components.gatewayPrefixesButton.addActionListener(e -> {
            String queryToRestore = searchField.getText();
            popup.cancel();
            ApplicationManager.getApplication().invokeLater(() -> {
                configureGatewayPrefixes(project, props);
                show(project, queryToRestore, currentModule);
            }, ModalityState.any());
        });

        Runnable navigateOrQueue = () -> {
            SearchPopupActions.NavigationResult result = SearchPopupActions.navigateToSelected(
                    resultList, popup, history, index, components.statusLabel);
            if (result != SearchPopupActions.NavigationResult.NAVIGATED) {
                navigateWhenResultsArrive.set(true);
                if (result == SearchPopupActions.NavigationResult.NO_SELECTION) {
                    index.ensureRebuildScheduled();
                    components.statusLabel.setText(index.isReady()
                            ? RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_WAITING_RESULTS)
                            : RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_INDEXING));
                }
            }
        };

        Runnable indexListener = () -> SwingUtilities.invokeLater(() -> {
            SearchController.refreshModuleFilter(components.moduleCombo, index.getItems(),
                    currentModule, components.moduleCombo.getSelectedItem());
            runSearch.run();
        });
        index.addListener(indexListener);

        popup.addListener(new JBPopupListener() {
            @Override
            public void beforeShown(@NotNull LightweightWindowEvent event) {
            }

            @Override
            public void onClosed(@NotNull LightweightWindowEvent event) {
                searchAlarm.cancelAllRequests();
                index.removeListener(indexListener);
                SearchPopupActions.recordWindowState(history, searchField.getText(), resultList);
                Object selected = components.moduleCombo.getSelectedItem();
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
        for (AbstractButton btn : Collections.list(components.methodGroup.getElements())) {
            btn.addActionListener(e -> {
                searchAlarm.cancelAllRequests();
                searchAlarm.addRequest(doSearch, 0);
            });
        }

        // Module combo triggers re-search
        components.moduleCombo.addActionListener(e -> {
            searchAlarm.cancelAllRequests();
            searchAlarm.addRequest(doSearch, 0);
        });

        // Selection listener for summary bar
        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                SearchResult selected = resultList.getSelectedValue();
                if (selected != null) {
                    history.recordSelectedEndpoint(searchField.getText(), selected.item());
                    SearchPopupActions.recordWindowState(history, searchField.getText(), resultList);
                    SearchPopupActions.updateSelectionSummary(components.selectionLabel, selected.item());
                } else {
                    components.selectionLabel.setText(" ");
                }
            }
        });

        // Keyboard & mouse events
        SearchPopupActions.installResultListKeyAdapter(resultList);
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateOrQueue.run();
                }
            }
        });
        SearchPopupActions.installSearchFieldNavigation(searchField, resultList);
        SearchPopupActions.installSearchFieldKeyAdapter(searchField, resultList, popup);
        SearchPopupActions.installEnterAction(components.mainPanel, navigateOrQueue);

        showPopup(project, popup);

        // Initial search
        SearchController.refreshModuleFilter(components.moduleCombo, index.getItems(),
                currentModule, components.moduleCombo.getSelectedItem());
        runSearch.run();

        IdeFocusManager.getInstance(project).requestFocus(searchField.getTextEditor(), true);
        searchField.getTextEditor().selectAll();
    }

    // --- Panel construction ---

    private static PopupComponents buildMainPanel(@NotNull SearchTextField searchField,
                                                  @NotNull JBList<SearchResult> resultList,
                                                  @NotNull JScrollPane resultScrollPane,
                                                  @NotNull EndpointIndex index,
                                                  @Nullable Module currentModule,
                                                  @NotNull PropertiesComponent props) {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(JBUI.size(POPUP_WIDTH, POPUP_HEIGHT));

        // Search field at top
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(JBUI.Borders.empty(4));
        JLabel searchLabel = new JLabel(" " + RestfulToolkitBundle.message(Keys.SEARCH_POPUP_SEARCH_LABEL) + " ");
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Method filter buttons
        JPanel methodFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        methodFilterPanel.setOpaque(false);
        methodFilterPanel.setBorder(JBUI.Borders.empty(0, 4));
        ButtonGroup methodGroup = new ButtonGroup();
        EnumMap<HttpMethod, JToggleButton> methodButtons = new EnumMap<>(HttpMethod.class);
        JToggleButton allBtn = SearchPopupActions.createFilterButton(
                RestfulToolkitBundle.message(Keys.SEARCH_POPUP_FILTER_ALL), methodGroup, methodFilterPanel);
        allBtn.setSelected(true);
        for (HttpMethod m : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH}) {
            methodButtons.put(m, SearchPopupActions.createFilterButton(m.name(), methodGroup, methodFilterPanel));
        }

        // Module filter dropdown
        JPanel moduleFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        moduleFilterPanel.setOpaque(false);
        JLabel moduleLabel = new JLabel(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_MODULE_LABEL) + " ");
        moduleLabel.setFont(moduleLabel.getFont().deriveFont(Font.PLAIN, moduleLabel.getFont().getSize() - 1f));
        moduleFilterPanel.add(moduleLabel);
        ComboBox<String> moduleCombo = new ComboBox<>();
        moduleCombo.setFont(moduleCombo.getFont().deriveFont(Font.PLAIN, moduleCombo.getFont().getSize() - 1f));
        String savedModule = props.getValue(SELECTED_MODULE_KEY);
        SearchController.refreshModuleFilter(moduleCombo, index.getItems(), currentModule, savedModule);
        moduleFilterPanel.add(moduleCombo);
        JButton gatewayPrefixesButton = new JButton(AllIcons.General.Settings);
        gatewayPrefixesButton.setToolTipText(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_GATEWAY_PREFIXES));
        gatewayPrefixesButton.setMargin(JBUI.emptyInsets());
        gatewayPrefixesButton.setPreferredSize(JBUI.size(24, 24));
        moduleFilterPanel.add(gatewayPrefixesButton);

        JLabel hintLabel = new JLabel(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_KEYBOARD_HINT));
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, hintLabel.getFont().getSize() - 2f));
        hintLabel.setForeground(JBColor.GRAY);
        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
        hintPanel.setOpaque(false);
        hintPanel.add(hintLabel);

        // Filter bar
        JPanel filterBar = new JPanel(new BorderLayout());
        filterBar.setOpaque(false);
        filterBar.add(methodFilterPanel, BorderLayout.WEST);
        filterBar.add(moduleFilterPanel, BorderLayout.EAST);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(searchPanel, BorderLayout.NORTH);
        topPanel.add(filterBar, BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        mainPanel.add(resultScrollPane, BorderLayout.CENTER);

        // Status bar
        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, statusLabel.getFont().getSize() - 2f));
        statusLabel.setForeground(JBColor.GRAY);

        JButton searchAllModulesBtn = new JButton(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_SEARCH_ALL_MODULES));
        searchAllModulesBtn.setFont(searchAllModulesBtn.getFont().deriveFont(Font.PLAIN, searchAllModulesBtn.getFont().getSize() - 2f));
        searchAllModulesBtn.setMargin(JBUI.insets(2, 8));
        searchAllModulesBtn.setVisible(false);
        searchAllModulesBtn.addActionListener(e -> moduleCombo.setSelectedIndex(0));

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

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(statusBar, BorderLayout.NORTH);
        bottomPanel.add(selectionBar, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        return new PopupComponents(mainPanel, methodGroup, methodButtons, moduleCombo,
                statusLabel, selectionLabel, searchAllModulesBtn, gatewayPrefixesButton);
    }

    private record PopupComponents(
            @NotNull JPanel mainPanel,
            @NotNull ButtonGroup methodGroup,
            @NotNull EnumMap<HttpMethod, JToggleButton> methodButtons,
            @NotNull ComboBox<String> moduleCombo,
            @NotNull JLabel statusLabel,
            @NotNull JLabel selectionLabel,
            @NotNull JButton searchAllModulesBtn,
            @NotNull JButton gatewayPrefixesButton
    ) {
    }

    private static void configureGatewayPrefixes(@NotNull Project project, @NotNull PropertiesComponent props) {
        GatewayPrefixesDialog dialog = new GatewayPrefixesDialog(project,
                props.getValue(PathSearchOptions.GATEWAY_PREFIXES_KEY, ""));
        if (!dialog.showAndGet()) {
            return;
        }
        props.setValue(PathSearchOptions.GATEWAY_PREFIXES_KEY, dialog.value().trim(), "");
    }

    private static final class GatewayPrefixesDialog extends DialogWrapper {
        private final JTextArea prefixesArea;

        private GatewayPrefixesDialog(@NotNull Project project, @NotNull String initialValue) {
            super(project, true);
            prefixesArea = new JTextArea(initialValue, 4, 36);
            prefixesArea.setLineWrap(true);
            prefixesArea.setWrapStyleWord(true);
            setTitle(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_GATEWAY_PREFIXES_TITLE));
            init();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(6)));
            panel.add(new JLabel(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_GATEWAY_PREFIXES_MESSAGE)),
                    BorderLayout.NORTH);
            panel.add(ScrollPaneFactory.createScrollPane(prefixesArea), BorderLayout.CENTER);
            return panel;
        }

        @Override
        public @Nullable JComponent getPreferredFocusedComponent() {
            return prefixesArea;
        }

        private @NotNull String value() {
            return prefixesArea.getText();
        }
    }

    private static void showPopup(@NotNull Project project, @NotNull JBPopup popup) {
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
    }

    // --- Backward-compatible delegates for tests ---

    static @NotNull String resolveInitialSearchText(@Nullable String initialText, @NotNull List<String> recentQueries) {
        return SearchPopupModel.resolveInitialSearchText(initialText, recentQueries);
    }

    static @NotNull String buildStatusText(@NotNull String text, int resultCount, int totalCount, boolean indexReady) {
        return SearchPopupModel.buildStatusText(text, resultCount, totalCount, indexReady);
    }

    static @NotNull String buildStatusText(@NotNull String text, int resultCount, int totalCount, boolean indexReady,
                                            @Nullable Module filterModule, @Nullable HttpMethod methodFilter) {
        return SearchPopupModel.buildStatusText(text, resultCount, totalCount, indexReady, filterModule, methodFilter);
    }

    static int findSelectionIndex(@NotNull List<SearchResult> results, @Nullable String preferredEndpointKey) {
        return SearchPopupModel.findSelectionIndex(results, preferredEndpointKey);
    }

    static int findSelectionIndex(@NotNull List<SearchResult> results,
                                  @Nullable String preferredEndpointKey,
                                  @Nullable Integer preferredSelectionIndex) {
        return SearchPopupModel.findSelectionIndex(results, preferredEndpointKey, preferredSelectionIndex);
    }

    static @NotNull List<SearchResult> buildRecentResults(@NotNull List<RestServiceItem> items,
                                                          @NotNull java.util.function.ToLongFunction<RestServiceItem> lastAccessLookup) {
        return SearchPopupModel.buildRecentResults(items, lastAccessLookup);
    }

    static void refreshModuleFilter(@NotNull JComboBox<String> combo,
                                    @NotNull List<RestServiceItem> items,
                                    @Nullable Module currentModule,
                                    @Nullable Object preferredSelection) {
        SearchController.refreshModuleFilter(combo, items, currentModule, preferredSelection);
    }

    static void moveFocusToResults(@NotNull JBList<SearchResult> resultList) {
        SearchPopupActions.moveFocusToResults(resultList);
    }

    static void moveSelectionFromSearchField(@NotNull JBList<SearchResult> resultList, int direction) {
        SearchPopupActions.moveSelectionFromSearchField(resultList, direction);
    }

    static void selectAndRevealIndex(@NotNull JBList<SearchResult> resultList, int index) {
        SearchPopupActions.selectAndRevealIndex(resultList, index);
    }

    static void selectAndRevealIndex(@NotNull JBList<SearchResult> resultList,
                                     int index,
                                     @Nullable Integer preferredFirstVisibleIndex) {
        SearchPopupActions.selectAndRevealIndex(resultList, index, preferredFirstVisibleIndex);
    }

    static void selectAndRevealIndex(@NotNull JBList<SearchResult> resultList,
                                     int index,
                                     @Nullable Integer preferredFirstVisibleIndex,
                                     @Nullable Integer preferredScrollY) {
        SearchPopupActions.selectAndRevealIndex(resultList, index, preferredFirstVisibleIndex, preferredScrollY);
    }
}
