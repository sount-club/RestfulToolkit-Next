package com.sount.restful.search;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.sount.restful.common.KtFunctionHelper;
import com.sount.restful.common.PsiMethodHelper;
import com.sount.restful.navigation.action.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.concurrent.Callable;

public class UnifiedSearchPreview extends JBPanel<UnifiedSearchPreview> {

    private static final String NO_DESCRIPTION = "No description";
    private static final String NO_QUERY_PARAMETERS = "No query parameters";
    private static final String LOADING_SCHEMA = "Loading request schema...";

    private final JBLabel methodLabel = new JBLabel();
    private final JTextArea endpointArea = new JTextArea();
    private final JTextArea sourceArea = new JTextArea();
    private final JTextArea paramsArea = new JTextArea();
    private final JTextArea bodyArea = new JTextArea();
    private final JTextArea descriptionArea = new JTextArea();
    private final Project myProject;
    private JPanel requestBodySection;

    static final int MAX_PREVIEW_WIDTH = JBUI.scale(360);

    public UnifiedSearchPreview(@NotNull Project project) {
        super(new BorderLayout());
        myProject = project;
        initUI();
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension superMax = super.getMaximumSize();
        return new Dimension(Math.min(superMax.width, MAX_PREVIEW_WIDTH), superMax.height);
    }

    private void initUI() {
        setBorder(JBUI.Borders.empty(8));

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);

        addPreviewRow(centerPanel, createHeaderPanel(), 0, 0, 0);
        addPreviewRow(centerPanel, createDescriptionPanel(), 1, 0, JBUI.scale(8));
        addPreviewRow(centerPanel, createCodeSection("Query / Params", paramsArea, 4), 2, 0.35, JBUI.scale(8));
        requestBodySection = createCodeSection("Request Body", bodyArea, 4);
        requestBodySection.setName("requestBodySection");
        requestBodySection.setVisible(false);
        addPreviewRow(centerPanel, requestBodySection, 3, 0.65, JBUI.scale(8));

        add(centerPanel, BorderLayout.CENTER);
    }

    private static void addPreviewRow(@NotNull JPanel parent,
                                      @NotNull JComponent component,
                                      int row,
                                      double weightY,
                                      int topInset) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.weighty = weightY;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = JBUI.insetsTop(topInset);
        parent.add(component, constraints);
    }

    private @NotNull JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(JBUI.scale(8), 4));
        panel.setOpaque(false);

        methodLabel.setOpaque(true);
        methodLabel.setHorizontalAlignment(SwingConstants.CENTER);
        methodLabel.setBorder(JBUI.Borders.empty(2, 8));
        methodLabel.setFont(methodLabel.getFont().deriveFont(Font.BOLD, methodLabel.getFont().getSize() - 1f));
        panel.add(methodLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        configureReadOnlyTextArea(endpointArea, UIUtil.getLabelFont().deriveFont(Font.BOLD, UIUtil.getLabelFont().getSize() + 2f), false);
        endpointArea.setBorder(JBUI.Borders.emptyBottom(3));
        endpointArea.setForeground(UIUtil.getLabelForeground());
        textPanel.add(endpointArea);

        configureReadOnlyTextArea(sourceArea, UIUtil.getLabelFont().deriveFont(Font.PLAIN, UIUtil.getLabelFont().getSize() - 1f), false);
        sourceArea.setForeground(JBColor.GRAY);
        sourceArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sourceArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                RestServiceItem item = currentItem;
                if (item != null && item.canNavigate()) {
                    if (onNavigate != null) {
                        onNavigate.run();
                    }
                    item.navigate(true);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                sourceArea.setForeground(JBColor.BLUE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                sourceArea.setForeground(JBColor.GRAY);
            }
        });
        textPanel.add(sourceArea);

        panel.add(textPanel, BorderLayout.CENTER);
        return panel;
    }

    private @NotNull JPanel createDescriptionPanel() {
        configureReadOnlyTextArea(descriptionArea, UIUtil.getLabelFont(), false);
        descriptionArea.setRows(3);
        descriptionArea.setForeground(JBColor.GRAY);
        return createSectionPanel("Description", descriptionArea);
    }

    private @NotNull JPanel createCodeSection(@NotNull String title, @NotNull JTextArea area, int rows) {
        configureReadOnlyTextArea(area, new Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().getSize()), true);
        area.setRows(rows);
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return createSectionPanel(title, scrollPane);
    }

    private @NotNull JPanel createSectionPanel(@NotNull String title, @NotNull JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JBLabel label = new JBLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(JBUI.Borders.emptyBottom(4));
        panel.add(label, BorderLayout.NORTH);

        content.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border()),
                JBUI.Borders.empty(6)
        ));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private static void configureReadOnlyTextArea(@NotNull JTextArea area, @NotNull Font font, boolean codeStyle) {
        area.setEditable(false);
        area.setEnabled(true);
        area.setFocusable(false);
        area.enableInputMethods(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(!codeStyle);
        area.setFont(font);
        area.setBackground(UIUtil.getPanelBackground());
        area.setBorder(JBUI.Borders.empty());
    }

    private volatile RestServiceItem currentItem;
    private @Nullable Runnable onNavigate;

    public void setOnNavigate(@Nullable Runnable onNavigate) {
        this.onNavigate = onNavigate;
    }

    public void showPreview(@NotNull RestServiceItem item) {
        currentItem = item;
        String method = item.getMethod() != null ? item.getMethod().name() : "?";
        String url = item.getUrl();
        String location = item.getLocationText();
        String moduleName = item.getModuleName();
        String javadoc = cleanJavadoc(item.getJavadoc());

        // Show basic info immediately on EDT
        SearchHistory history = SearchHistory.getInstance(myProject);
        boolean isFav = history.isFavorite(item);
        methodLabel.setText(method);
        methodLabel.setForeground(methodForeground(method));
        methodLabel.setBackground(methodBackground(method));
        endpointArea.setText((isFav ? "\u2605 " : "") + url);
        endpointArea.setToolTipText(url);
        sourceArea.setText(buildSourceText(location, moduleName, ""));
        descriptionArea.setText(displayOrEmpty(javadoc, NO_DESCRIPTION));
        paramsArea.setText(LOADING_SCHEMA);
        updateRequestBodySection("");

        // Compute params/body off EDT to avoid slow-operation prohibition on EDT.
        // PSI index access (JavaPsiFacade.findClass) is a slow operation.
        Callable<String[]> computeTask = () -> {
            if (currentItem != item) return new String[]{"", ""}; // stale
            String requestParams = "";
            String requestBody = "";

            if (item.getPsiElement().getLanguage() == JavaLanguage.INSTANCE && item.getPsiMethod() != null) {
                PsiMethodHelper helper = PsiMethodHelper.create(item.getPsiMethod()).withModule(item.getModule());
                requestParams = helper.buildParamString().replace("&", "\n");
                requestBody = helper.buildRequestBodyJson();
            } else if (item.getPsiElement().getLanguage() == KotlinLanguage.INSTANCE && item.getPsiElement() instanceof KtNamedFunction ktFunc) {
                KtFunctionHelper helper = KtFunctionHelper.create(ktFunc).withModule(item.getModule());
                requestParams = helper.buildParamString().replace("&", "\n");
                requestBody = helper.buildRequestBodyJson();
            }

            return new String[]{requestParams != null ? requestParams : "",
                    requestBody != null ? requestBody : ""};
        };
        ReadAction.nonBlocking(computeTask).finishOnUiThread(ModalityState.defaultModalityState(), result -> {
            if (!Objects.equals(currentItem, item)) {
                return; // stale
            }
            paramsArea.setText(displayOrEmpty(result[0], NO_QUERY_PARAMETERS));
            updateRequestBodySection(result[1]);
        }).submit(AppExecutorUtil.getAppExecutorService());
    }

    public void clearPreview() {
        methodLabel.setText("");
        methodLabel.setBackground(UIUtil.getPanelBackground());
        endpointArea.setText("Select an endpoint");
        endpointArea.setToolTipText(null);
        sourceArea.setText("");
        descriptionArea.setText("");
        paramsArea.setText("");
        updateRequestBodySection("");
    }

    private void updateRequestBodySection(@Nullable String requestBody) {
        boolean hasRequestBody = requestBody != null && !requestBody.isBlank();
        bodyArea.setText(hasRequestBody ? requestBody : "");
        if (requestBodySection != null) {
            requestBodySection.setVisible(hasRequestBody);
            revalidate();
            repaint();
        }
    }

    static @NotNull String cleanJavadoc(@Nullable String text) {
        if (text == null || text.isBlank()) return "";
        String cleaned = text
                .replace("/**", "")
                .replace("*/", "")
                .replaceAll("(?m)^\\s*\\*\\s?", "")
                .trim();
        return cleaned;
    }

    static @NotNull String displayOrEmpty(@Nullable String text, @NotNull String emptyText) {
        return text == null || text.isBlank() ? emptyText : text;
    }

    static @NotNull String buildSourceText(@NotNull String location,
                                           @Nullable String moduleName,
                                           @Nullable String packageName) {
        StringBuilder builder = new StringBuilder(location);
        if (moduleName != null && !moduleName.isEmpty()) {
            builder.append("  [").append(moduleName).append("]");
        }
        if (packageName != null && !packageName.isEmpty()) {
            builder.append("  ").append(packageName);
        }
        return builder.toString();
    }

    private static @NotNull Color methodForeground(@NotNull String method) {
        return switch (method) {
            case "GET", "POST", "PUT", "DELETE", "PATCH" -> UIUtil.getListSelectionForeground(true);
            default -> UIUtil.getLabelForeground();
        };
    }

    private static @NotNull Color methodBackground(@NotNull String method) {
        return switch (method) {
            case "GET" -> new JBColor(new Color(0xE6F4EA), new Color(0x1F4D2B));
            case "POST" -> new JBColor(new Color(0xE8F0FE), new Color(0x1E3A5F));
            case "PUT" -> new JBColor(new Color(0xFFF4E5), new Color(0x5A3B12));
            case "DELETE" -> new JBColor(new Color(0xFCE8E6), new Color(0x5C1F1B));
            case "PATCH" -> new JBColor(new Color(0xF1F3F4), new Color(0x3C4043));
            default -> UIUtil.getPanelBackground();
        };
    }
}
