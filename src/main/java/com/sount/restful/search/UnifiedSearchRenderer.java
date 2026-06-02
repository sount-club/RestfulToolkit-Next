package com.sount.restful.search;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.sount.restful.common.ToolkitIcons;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.navigation.action.RestServiceItem;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class UnifiedSearchRenderer extends JPanel implements ListCellRenderer<SearchResult> {

    private static final JBColor METHOD_COLOR_GET = new JBColor(new Color(0, 150, 0), new Color(104, 205, 104));
    private static final JBColor METHOD_COLOR_POST = new JBColor(new Color(0, 100, 200), new Color(80, 150, 230));
    private static final JBColor METHOD_COLOR_PUT = new JBColor(new Color(200, 130, 0), new Color(220, 170, 60));
    private static final JBColor METHOD_COLOR_DELETE = new JBColor(new Color(200, 50, 50), new Color(230, 90, 90));
    private static final JBColor METHOD_COLOR_PATCH = new JBColor(new Color(130, 130, 130), new Color(160, 160, 160));

    private static final JBColor METHOD_BG_GET = new JBColor(new Color(0xE6F4EA), new Color(0x1F4D2B));
    private static final JBColor METHOD_BG_POST = new JBColor(new Color(0xE8F0FE), new Color(0x1E3A5F));
    private static final JBColor METHOD_BG_PUT = new JBColor(new Color(0xFFF4E5), new Color(0x5A3B12));
    private static final JBColor METHOD_BG_DELETE = new JBColor(new Color(0xFCE8E6), new Color(0x5C1F1B));
    private static final JBColor METHOD_BG_PATCH = new JBColor(new Color(0xF1F3F4), new Color(0x3C4043));

    private static final JBColor CHIP_BACKGROUND = new JBColor(new Color(0xE8F0FE), new Color(0x1E3A5F));
    private static final JBColor CHIP_FOREGROUND = new JBColor(new Color(0x1A73E8), new Color(0x8AB4F8));
    private static final Border CHIP_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CHIP_FOREGROUND, 1, true),
            JBUI.Borders.empty(1, 4));

    private static final Map<String, String> FIELD_CHIP_LABELS = Map.of(
            MatchField.PATH, "命中路径",
            MatchField.METHOD_NAME, "命中方法名",
            MatchField.DESCRIPTION, "命中描述",
            MatchField.HTTP_METHOD, "命中Method",
            MatchField.MODULE_NAME, "命中模块",
            MatchField.CONTROLLER_NAME, "命中Controller"
    );

    // Pre-allocated attributes to avoid per-render allocation
    private static final SimpleTextAttributes URL_BOLD = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, null);
    private static final SimpleTextAttributes HIGHLIGHT_ATTRS = new SimpleTextAttributes(
            SimpleTextAttributes.STYLE_BOLD | SimpleTextAttributes.STYLE_SEARCH_MATCH, null);
    private static final SimpleTextAttributes DIM_NORMAL = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY);
    private static final SimpleTextAttributes DIM_SELECTED = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, null); // fg set at render time

    // Line 1 components
    private final JLabel iconLabel = new JLabel();
    private final JLabel methodLabel = new JLabel();
    private final SimpleColoredComponent urlComponent = new SimpleColoredComponent();
    private final JLabel bestMatchBadge = new JLabel("Best match");

    // Line 2 components
    private final SimpleColoredComponent descLineComponent = new SimpleColoredComponent();

    // Line 3 components
    private final JPanel chipsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));

    private List<String> highlightTokens = Collections.emptyList();

    public UnifiedSearchRenderer() {
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty(4, 4));

        // Line 1: method icon + method text + URL + best match badge
        JPanel line1 = new JPanel(new BorderLayout(JBUI.scale(4), 0));
        line1.setOpaque(false);

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0));
        leftPanel.setOpaque(false);
        leftPanel.add(iconLabel);

        methodLabel.setFont(methodLabel.getFont().deriveFont(Font.BOLD, methodLabel.getFont().getSize() - 1f));
        leftPanel.add(methodLabel);
        line1.add(leftPanel, BorderLayout.WEST);

        urlComponent.setOpaque(false);
        line1.add(urlComponent, BorderLayout.CENTER);

        bestMatchBadge.setFont(bestMatchBadge.getFont().deriveFont(Font.PLAIN, bestMatchBadge.getFont().getSize() - 2f));
        bestMatchBadge.setForeground(JBColor.GREEN.darker());
        bestMatchBadge.setVisible(false);
        line1.add(bestMatchBadge, BorderLayout.EAST);

        // Line 2: description · Controller#methodName · moduleName
        descLineComponent.setOpaque(false);
        Font descFont = UIUtil.getLabelFont();
        descLineComponent.setFont(descFont.deriveFont(Font.PLAIN, descFont.getSize() - 2f));
        descLineComponent.setIpad(JBUI.emptyInsets());

        // Line 3: match field chips
        chipsPanel.setOpaque(false);
        chipsPanel.setVisible(false);

        // Layout: vertical box
        JPanel contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.add(line1);
        contentPanel.add(descLineComponent);
        contentPanel.add(chipsPanel);

        add(contentPanel, BorderLayout.CENTER);
    }

    public void setHighlightTokens(@Nullable List<String> tokens) {
        this.highlightTokens = (tokens != null && !tokens.isEmpty()) ? tokens : Collections.emptyList();
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends SearchResult> list, SearchResult value,
                                                  int index, boolean isSelected, boolean cellHasFocus) {
        RestServiceItem item = value.item();

        // Method icon
        HttpMethod method = item.getMethod();
        iconLabel.setIcon(ToolkitIcons.METHOD.get(method));

        // Method text with color
        String methodText = item.getMethodText();
        methodLabel.setText(methodText != null ? methodText : "?");
        methodLabel.setForeground(getMethodColor(method));
        if (isSelected) {
            methodLabel.setOpaque(true);
            methodLabel.setBackground(getMethodBgColor(method));
            methodLabel.setBorder(JBUI.Borders.empty(1, 4));
        } else {
            methodLabel.setOpaque(false);
            methodLabel.setBorder(null);
        }

        // URL with multi-token highlighting
        urlComponent.clear();
        String url = item.getUrl() != null ? item.getUrl() : "";
        SimpleTextAttributes urlBase = isSelected ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : URL_BOLD;
        appendHighlightedMultiToken(urlComponent, url, highlightTokens, urlBase);

        // Best match badge (only for first result)
        bestMatchBadge.setVisible(index == 0 && !highlightTokens.isEmpty());

        // Line 2: Controller#method · module
        descLineComponent.clear();
        String controllerName = item.getControllerName();
        String methodName = item.getMethodName();
        String moduleName = item.getModuleName();

        SimpleTextAttributes dimAttrs = isSelected ? DIM_SELECTED : DIM_NORMAL;

        boolean hasPrev = false;
        if (controllerName != null && !controllerName.isEmpty() && methodName != null && !methodName.isEmpty()) {
            String controllerMethod = controllerName + "#" + methodName;
            appendHighlightedMultiToken(descLineComponent, controllerMethod, highlightTokens, dimAttrs);
            hasPrev = true;
        }
        if (moduleName != null && !moduleName.isEmpty()) {
            if (hasPrev) descLineComponent.append(" · ", dimAttrs);
            appendHighlightedMultiToken(descLineComponent, moduleName, highlightTokens, dimAttrs);
        }

        // Tooltip with full endpoint info
        String sourceText = buildSourceText(item);
        setToolTipText(sourceText.isEmpty() ? null : sourceText);

        // Line 3: match field chips
        Set<String> matchedFields = value.matchedFields();
        chipsPanel.removeAll();
        if (matchedFields != null && !matchedFields.isEmpty()) {
            for (String field : matchedFields) {
                String label = FIELD_CHIP_LABELS.get(field);
                if (label != null) {
                    JLabel chip = createChip(label, isSelected);
                    chipsPanel.add(chip);
                }
            }
            chipsPanel.setVisible(true);
        } else {
            chipsPanel.setVisible(false);
        }

        // Selection colors
        if (isSelected) {
            setBackground(UIUtil.getListSelectionBackground(true));
            urlComponent.setForeground(UIUtil.getListSelectionForeground(true));
        } else {
            setBackground(UIUtil.getListBackground());
            urlComponent.setForeground(UIUtil.getListForeground());
        }

        return this;
    }

    private JLabel createChip(String text, boolean isSelected) {
        JLabel chip = new JLabel(text);
        chip.setFont(chip.getFont().deriveFont(Font.PLAIN, chip.getFont().getSize() - 3f));
        if (isSelected) {
            chip.setForeground(UIUtil.getListSelectionForeground(true));
            chip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIUtil.getListSelectionForeground(true), 1, true),
                    JBUI.Borders.empty(1, 4)));
        } else {
            chip.setForeground(CHIP_FOREGROUND);
            chip.setBorder(CHIP_BORDER);
        }
        chip.setOpaque(false);
        return chip;
    }

    private void appendHighlightedMultiToken(SimpleColoredComponent component, String text,
                                              List<String> tokens, SimpleTextAttributes baseAttributes) {
        if (tokens.isEmpty()) {
            component.append(text, baseAttributes);
            return;
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        int pos = 0;
        while (pos < text.length()) {
            int bestStart = -1;
            int bestEnd = -1;
            for (String token : tokens) {
                String lt = token.toLowerCase(Locale.ROOT);
                int idx = lowerText.indexOf(lt, pos);
                if (idx >= 0) {
                    int end = idx + lt.length();
                    if (bestStart < 0 || idx < bestStart || (idx == bestStart && end > bestEnd)) {
                        bestStart = idx;
                        bestEnd = end;
                    }
                }
            }
            if (bestStart < 0) {
                component.append(text.substring(pos), baseAttributes);
                break;
            }
            if (bestStart > pos) {
                component.append(text.substring(pos, bestStart), baseAttributes);
            }
            component.append(text.substring(bestStart, bestEnd), HIGHLIGHT_ATTRS);
            pos = bestEnd;
        }
    }

    static String buildSourceText(RestServiceItem item) {
        String location = item.getLocationText();
        String moduleName = item.getModuleName();
        String sourceText = location != null ? location : "";
        if (moduleName != null && !moduleName.isEmpty()) {
            sourceText = sourceText.isEmpty() ? "[" + moduleName + "]" : sourceText + " [" + moduleName + "]";
        }
        return sourceText;
    }

    private static JBColor getMethodColor(HttpMethod method) {
        if (method == null) return JBColor.GRAY;
        return switch (method) {
            case GET -> METHOD_COLOR_GET;
            case POST -> METHOD_COLOR_POST;
            case PUT -> METHOD_COLOR_PUT;
            case DELETE -> METHOD_COLOR_DELETE;
            case PATCH -> METHOD_COLOR_PATCH;
            default -> JBColor.GRAY;
        };
    }

    private static JBColor getMethodBgColor(HttpMethod method) {
        if (method == null) return JBColor.GRAY;
        return switch (method) {
            case GET -> METHOD_BG_GET;
            case POST -> METHOD_BG_POST;
            case PUT -> METHOD_BG_PUT;
            case DELETE -> METHOD_BG_DELETE;
            case PATCH -> METHOD_BG_PATCH;
            default -> JBColor.GRAY;
        };
    }
}
