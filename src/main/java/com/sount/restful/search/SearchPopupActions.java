package com.sount.restful.search;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.ui.JBColor;
import com.sount.restful.navigation.RestServiceItem;
import com.sount.restful.utils.RestfulToolkitBundle;
import com.sount.restful.utils.RestfulToolkitBundle.Keys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * User interaction actions for the unified search popup:
 * keyboard shortcuts, mouse events, navigation, and clipboard operations.
 */
final class SearchPopupActions {

    private SearchPopupActions() {
    }

    // --- Navigation ---

    static void navigateToSelected(@NotNull JBList<SearchResult> resultList,
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

    static void copySelectedPath(@NotNull JBList<SearchResult> resultList) {
        SearchResult selected = resultList.getSelectedValue();
        if (selected != null) {
            String path = selected.item().getUrl();
            if (path != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(path), null);
            }
        }
    }

    // --- Selection ---

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

    static void recordWindowState(@NotNull SearchHistory history,
                                  @NotNull String query,
                                  @NotNull JBList<SearchResult> resultList) {
        history.recordWindowState(query, resultList.getSelectedIndex(), resultList.getFirstVisibleIndex(),
                resultList.getVisibleRect().y);
    }

    // --- Keyboard binding ---

    static void installSearchFieldNavigation(@NotNull SearchTextField searchField,
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

    static void installResultListKeyAdapter(@NotNull JBList<SearchResult> resultList,
                                            @NotNull JBPopup popup,
                                            @NotNull SearchHistory history) {
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
    }

    static void installSearchFieldKeyAdapter(@NotNull SearchTextField searchField,
                                             @NotNull JBList<SearchResult> resultList,
                                             @NotNull JBPopup popup,
                                             @NotNull SearchHistory history) {
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
    }

    // --- UI helpers ---

    static void updateSelectionSummary(@NotNull JLabel selectionLabel, @NotNull RestServiceItem item) {
        String method = item.getMethod() != null ? item.getMethod().name() : "?";
        String url = item.getUrl() != null ? item.getUrl() : "";
        String controllerName = item.getControllerName();
        String methodName = item.getMethodName();
        String moduleName = item.getModuleName();

        StringBuilder sb = new StringBuilder();
        sb.append(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_SELECTION_CURRENT)).append(method).append(" ").append(url);
        if (controllerName != null && !controllerName.isEmpty() && methodName != null && !methodName.isEmpty()) {
            sb.append(" · ").append(controllerName).append("#").append(methodName);
        }
        if (moduleName != null && !moduleName.isEmpty()) {
            sb.append(" · ").append(moduleName);
        }
        selectionLabel.setText(sb.toString());
    }

    static JToggleButton createFilterButton(String text, ButtonGroup group, JPanel panel) {
        JToggleButton btn = new JToggleButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (isSelected()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(new JBColor(
                            new Color(0x4A90D9), new Color(0x6AA8F0)));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
                    g2.dispose();
                }
            }
        };
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, btn.getFont().getSize() - 2f));
        btn.setMargin(JBUI.insets(2, 6, 2, 6));
        btn.setFocusPainted(false);

        btn.addChangeListener(e -> btn.repaint());

        group.add(btn);
        panel.add(btn);
        return btn;
    }
}
