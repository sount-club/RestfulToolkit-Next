package com.sount.restful.search;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.sount.restful.navigation.action.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.concurrent.Callable;

public class UnifiedSearchPreview extends JBPanel<UnifiedSearchPreview> {

    private static final String NO_DESCRIPTION = "No description";
    private static final String NO_METHOD_CODE = "No method source available";
    private static final String LOADING_METHOD_CODE = "Loading method code...";

    private final JBLabel methodLabel = new JBLabel();
    private final JTextArea endpointArea = new JTextArea();
    private final JTextArea sourceArea = new JTextArea();
    private final EditorTextField methodCodeEditor;
    private final JTextArea descriptionArea = new JTextArea();
    private final Project myProject;

    static final int MAX_PREVIEW_WIDTH = JBUI.scale(360);

    public UnifiedSearchPreview(@NotNull Project project) {
        super(new BorderLayout());
        myProject = project;
        methodCodeEditor = createMethodCodeEditor(project);
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
        JPanel methodCodeSection = createSectionPanel("Method Code", methodCodeEditor);
        methodCodeSection.setName("methodCodeSection");
        addPreviewRow(centerPanel, methodCodeSection, 2, 1, JBUI.scale(8));

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

    private static @NotNull EditorTextField createMethodCodeEditor(@NotNull Project project) {
        Document document = EditorFactory.getInstance().createDocument("");
        EditorTextField editor = new EditorTextField(document, project, PlainTextFileType.INSTANCE, true, false);
        editor.setName("methodCodeEditor");
        editor.setOneLineMode(false);
        editor.setViewer(true);
        editor.addSettingsProvider(UnifiedSearchPreview::configureMethodCodeEditor);
        return editor;
    }

    private static void configureMethodCodeEditor(@NotNull EditorEx editor) {
        editor.setHorizontalScrollbarVisible(true);
        editor.setVerticalScrollbarVisible(true);
        editor.getSettings().setUseSoftWraps(false);
        editor.getSettings().setLineNumbersShown(true);
        editor.getSettings().setLineMarkerAreaShown(false);
        editor.getSettings().setFoldingOutlineShown(false);
        editor.getSettings().setIndentGuidesShown(true);
        editor.getSettings().setAdditionalColumnsCount(3);
        editor.getSettings().setAdditionalLinesCount(1);
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
        String description = item.getDescription();

        // Show basic info immediately on EDT
        SearchHistory history = SearchHistory.getInstance(myProject);
        boolean isFav = history.isFavorite(item);
        methodLabel.setText(method);
        methodLabel.setForeground(methodForeground(method));
        methodLabel.setBackground(methodBackground(method));
        endpointArea.setText((isFav ? "\u2605 " : "") + url);
        endpointArea.setToolTipText(url);
        sourceArea.setText(buildSourceText(location, moduleName, ""));
        descriptionArea.setText(displayOrEmpty(description, NO_DESCRIPTION));
        setMethodCode(PlainTextFileType.INSTANCE, LOADING_METHOD_CODE);

        Callable<Object[]> computeTask = () -> {
            if (currentItem != item) return new Object[]{PlainTextFileType.INSTANCE, ""};
            PsiElement el = item.getPsiElement();
            return new Object[]{resolveMethodCodeFileType(el), buildMethodCode(el)};
        };
        ReadAction.nonBlocking(computeTask).finishOnUiThread(ModalityState.defaultModalityState(), result -> {
            if (!Objects.equals(currentItem, item) || result == null) {
                return; // stale
            }
            FileType fileType = (FileType) result[0];
            String code = (String) result[1];
            setMethodCode(fileType, displayOrEmpty(code, NO_METHOD_CODE));
        }).submit(AppExecutorUtil.getAppExecutorService());
    }

    public void clearPreview() {
        methodLabel.setText("");
        methodLabel.setBackground(UIUtil.getPanelBackground());
        endpointArea.setText("Select an endpoint");
        endpointArea.setToolTipText(null);
        sourceArea.setText("");
        descriptionArea.setText("");
        setMethodCode(PlainTextFileType.INSTANCE, "");
    }

    private void setMethodCode(@NotNull FileType fileType, @NotNull String text) {
        Document document = EditorFactory.getInstance().createDocument(text);
        methodCodeEditor.setNewDocumentAndFileType(fileType, document);
        methodCodeEditor.setCaretPosition(0);
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

    static @NotNull String buildMethodCode(@Nullable com.intellij.psi.PsiElement element) {
        if (element == null || !element.isValid()) {
            return "";
        }
        return element.getText();
    }

    static @NotNull FileType resolveMethodCodeFileType(@Nullable com.intellij.psi.PsiElement element) {
        if (element == null || !element.isValid() || element.getContainingFile() == null) {
            return PlainTextFileType.INSTANCE;
        }
        FileType fileType = element.getContainingFile().getFileType();
        return fileType != null ? fileType : PlainTextFileType.INSTANCE;
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
