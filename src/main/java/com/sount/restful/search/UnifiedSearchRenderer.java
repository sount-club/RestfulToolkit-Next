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
import java.awt.*;
import java.util.Locale;

public class UnifiedSearchRenderer extends JPanel implements ListCellRenderer<SearchResult> {

    private static final JBColor METHOD_COLOR_GET = new JBColor(new Color(0, 150, 0), new Color(104, 205, 104));
    private static final JBColor METHOD_COLOR_POST = new JBColor(new Color(0, 100, 200), new Color(80, 150, 230));
    private static final JBColor METHOD_COLOR_PUT = new JBColor(new Color(200, 130, 0), new Color(220, 170, 60));
    private static final JBColor METHOD_COLOR_DELETE = new JBColor(new Color(200, 50, 50), new Color(230, 90, 90));
    private static final JBColor METHOD_COLOR_PATCH = new JBColor(new Color(130, 130, 130), new Color(160, 160, 160));

    private final JLabel iconLabel = new JLabel();
    private final JLabel methodLabel = new JLabel();
    private final SimpleColoredComponent urlComponent = new SimpleColoredComponent();
    private final SimpleColoredComponent sourceComponent = new SimpleColoredComponent();
    private final JLabel favoriteLabel = new JLabel();

    private @Nullable String highlightPattern;

    public UnifiedSearchRenderer() {
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty(3, 4));

        JPanel topPanel = new JPanel(new BorderLayout(JBUI.scale(4), 0));
        topPanel.setOpaque(false);

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0));
        leftPanel.setOpaque(false);
        leftPanel.add(iconLabel);

        methodLabel.setFont(methodLabel.getFont().deriveFont(Font.BOLD, methodLabel.getFont().getSize() - 1f));
        leftPanel.add(methodLabel);

        topPanel.add(leftPanel, BorderLayout.WEST);

        urlComponent.setOpaque(false);
        topPanel.add(urlComponent, BorderLayout.CENTER);

        favoriteLabel.setIcon(AllIcons.Nodes.Favorite);
        favoriteLabel.setVisible(false);
        topPanel.add(favoriteLabel, BorderLayout.EAST);

        sourceComponent.setOpaque(false);
        sourceComponent.setName("UnifiedSearchSource");
        Font sourceFont = UIUtil.getLabelFont();
        sourceComponent.setFont(sourceFont.deriveFont(Font.PLAIN, sourceFont.getSize() - 2f));
        sourceComponent.setIpad(JBUI.emptyInsets());

        JPanel sourcePanel = new JPanel(new BorderLayout());
        sourcePanel.setOpaque(false);
        sourcePanel.add(sourceComponent, BorderLayout.EAST);

        JPanel contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.add(topPanel);
        contentPanel.add(sourcePanel);

        add(contentPanel, BorderLayout.CENTER);
    }

    public void setHighlightPattern(@Nullable String pattern) {
        this.highlightPattern = (pattern != null && !pattern.isEmpty()) ? pattern.toLowerCase(Locale.ROOT) : null;
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
        methodLabel.setForeground(isSelected ? UIUtil.getListSelectionForeground(true) : getMethodColor(method));

        // URL with highlighting
        urlComponent.clear();
        String url = item.getUrl() != null ? item.getUrl() : "";
        appendHighlighted(urlComponent, url, highlightPattern,
                isSelected ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, null));

        // Source: ClassName#methodName [module]
        String sourceText = buildSourceText(item);
        sourceComponent.clear();
        sourceComponent.append(sourceText, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY));
        setToolTipText(sourceText.isEmpty() ? null : sourceText);

        // Favorite visibility
        favoriteLabel.setVisible(false);

        // Selection colors
        if (isSelected) {
            setBackground(UIUtil.getListSelectionBackground(true));
            urlComponent.setForeground(UIUtil.getListSelectionForeground(true));
            sourceComponent.setForeground(UIUtil.getListSelectionForeground(true));
        } else {
            setBackground(UIUtil.getListBackground());
            urlComponent.setForeground(UIUtil.getListForeground());
            sourceComponent.setForeground(JBColor.GRAY);
        }

        return this;
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

    private void appendHighlighted(SimpleColoredComponent component, String text, @Nullable String pattern,
                                   SimpleTextAttributes baseAttributes) {
        if (pattern == null || pattern.isEmpty()) {
            component.append(text, baseAttributes);
            return;
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        int pos = 0;
        while (pos < text.length()) {
            int matchStart = lowerText.indexOf(pattern, pos);
            if (matchStart < 0) {
                component.append(text.substring(pos), baseAttributes);
                break;
            }

            if (matchStart > pos) {
                component.append(text.substring(pos, matchStart), baseAttributes);
            }

            SimpleTextAttributes highlightAttrs = new SimpleTextAttributes(
                    SimpleTextAttributes.STYLE_BOLD | SimpleTextAttributes.STYLE_SEARCH_MATCH,
                    baseAttributes.getFgColor());
            component.append(text.substring(matchStart, matchStart + pattern.length()), highlightAttrs);
            pos = matchStart + pattern.length();
        }
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
}
