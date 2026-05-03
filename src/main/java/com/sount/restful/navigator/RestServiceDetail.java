package com.sount.restful.navigator;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.*;

import com.google.gson.*;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.sount.restful.common.RequestHelper;
import com.sount.restful.method.HttpMethod;
import com.sount.utils.JsonUtils;
import com.sount.utils.ToolkitUtil;
import org.apache.commons.lang3.StringUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jetbrains.annotations.NotNull;

//import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions;
//import com.intellij.ui.components.JBPanelWithEmptyText;

public class RestServiceDetail extends JBPanel/*WithEmptyText*/ {

	public JTextField urlField;
	public JPanel urlPanel;
	public JComboBox<String> methodField;
	public JButton sendButton;
	public JTabbedPane requestTabbedPane;

	public RSyntaxTextArea requestParamsTextArea;
	public RSyntaxTextArea requestHeadersTextArea;
	public RSyntaxTextArea requestBodyTextArea;
	public RSyntaxTextArea responseTextArea;
	private JLabel endpointSummaryLabel;
	private JLabel sourceInfoLabel;
	private JLabel responseMetaLabel;
	private JLabel requestModeHintLabel;
	private JPanel headerPanel;
	private JComboBox<String> bodyTypeField;
	private JButton formatButton;
	private JButton templateButton;
	private static final String[] SUPPORTED_METHODS = {
			HttpMethod.GET.name(),
			HttpMethod.POST.name(),
			HttpMethod.PUT.name(),
			HttpMethod.DELETE.name(),
			HttpMethod.PATCH.name()
	};
	private static final String BODY_TYPE_NONE = "NONE";
	private static final String BODY_TYPE_JSON = "JSON";
	private static final String BODY_TYPE_FORM = "FORM";
	private static final String QUERY_TAB_TITLE = "Query / Path";
	private static final String HEADERS_TAB_TITLE = "Headers";
	private static final String BODY_TAB_TITLE = "Body";
	private static final String JSON_BODY_TAB_TITLE = "JSON Body";
	private static final String FORM_BODY_TAB_TITLE = "Form Body";
	private static final String RESPONSE_TAB_TITLE = "Response";
	private static final String QUERY_TEMPLATE = "// Query/path params. Example:\n// page : 1\n// keyword : demo";
	private static final String HEADERS_TEMPLATE = "// Request headers. Example:\n// Authorization : Bearer <token>";
	private static final String BODY_NONE_TEMPLATE = "// Body disabled. Change Body mode to JSON or FORM.";
	private static final String BODY_JSON_TEMPLATE = "{\n  \"example\": \"value\"\n}";
	private static final String BODY_FORM_TEMPLATE = "// Form fields. Example:\n// username : admin\n// password : 123456";

	public RestServiceDetail(Project project) {
		super();
		initComponent();
	}

	public static RestServiceDetail getInstance(Project p) {
		return p.getService(RestServiceDetail.class);
	}

	public void initComponent() {
		initUI();
		initActions();
		initTab();
		setEndpointSummary("Select an endpoint to inspect and test");
		setSourceValue("Search by URL, method, module, or source");
		clearResponseMeta();
		updateRequestModeHint();
	}

	private void initActions() {
		bindSendButtonActionListener();
		bindUrlTextActionListener();
	}

	public void initTab() {
		if (requestTabbedPane.getTabCount() == 0) {
			addRequestParamsTab("");
			addRequestHeadersTab("");
			addRequestBodyTabPanel("");
		}
	}

	@Override
	protected void printComponent(Graphics g) {
		super.printComponent(g);
	}

	private void initUI() {
		urlField.setAutoscrolls(true);
		methodField.setEditable(false);
		methodField.setModel(new DefaultComboBoxModel<>(SUPPORTED_METHODS));
		methodField.setSelectedItem(HttpMethod.GET.name());
		methodField.setFocusable(false);
		methodField.setPrototypeDisplayValue("OPTIONS");
		methodField.addActionListener(e -> updateRequestModeHint());
		bodyTypeField = new JComboBox<>(new String[]{BODY_TYPE_NONE, BODY_TYPE_JSON, BODY_TYPE_FORM});
		bodyTypeField.setSelectedItem(BODY_TYPE_NONE);
		bodyTypeField.setFocusable(false);
		bodyTypeField.addActionListener(e -> syncBodyEditorMode());
		formatButton = new JButton("Format");
		formatButton.setFocusable(false);
		formatButton.addActionListener(e -> formatSelectedEditor());
		templateButton = new JButton("Template");
		templateButton.setFocusable(false);
		templateButton.addActionListener(e -> applyTemplateForSelectedTab());
		sendButton.putClientProperty("JButton.buttonType", "default");
		sendButton.setFocusable(false);

		endpointSummaryLabel = new JLabel();
		endpointSummaryLabel.setFont(endpointSummaryLabel.getFont().deriveFont(Font.BOLD, endpointSummaryLabel.getFont().getSize2D() + 1f));

		sourceInfoLabel = new JLabel();
		sourceInfoLabel.setForeground(JBColor.GRAY);

		responseMetaLabel = new JLabel();
		responseMetaLabel.setForeground(JBColor.GRAY);

		requestModeHintLabel = new JLabel();
		requestModeHintLabel.setForeground(JBColor.GRAY);

		JPanel summaryPanel = new JBPanel();
		summaryPanel.setOpaque(false);
		summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
		summaryPanel.add(endpointSummaryLabel);
		summaryPanel.add(Box.createVerticalStrut(JBUI.scale(2)));
		summaryPanel.add(sourceInfoLabel);

		urlPanel = new JBPanel();
		GridLayoutManager mgr = new GridLayoutManager(1, 3);
		mgr.setHGap(JBUI.scale(6));
		mgr.setVGap(JBUI.scale(4));
		urlPanel.setLayout(mgr);

		urlPanel.add(methodField,
				new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_SOUTHEAST, GridConstraints.FILL_BOTH,
						GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
						null, null, null));
		urlPanel.add(urlField,
				new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_SOUTHEAST, GridConstraints.FILL_BOTH,
						GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
						GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
						null, null, null));
		urlPanel.add(sendButton,
				new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_SOUTHEAST, GridConstraints.FILL_BOTH,
						GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
						null, null, null));

		headerPanel = new JBPanel(new BorderLayout(0, JBUI.scale(8)));
		headerPanel.setOpaque(false);
		headerPanel.setBorder(JBUI.Borders.empty(0, 0, 8, 0));
		headerPanel.add(summaryPanel, BorderLayout.NORTH);
		headerPanel.add(urlPanel, BorderLayout.CENTER);

		JPanel footerPanel = new JBPanel(new BorderLayout(JBUI.scale(8), 0));
		footerPanel.setOpaque(false);
		JPanel infoPanel = new JBPanel();
		infoPanel.setOpaque(false);
		infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
		infoPanel.add(responseMetaLabel);
		infoPanel.add(Box.createVerticalStrut(JBUI.scale(4)));
		infoPanel.add(requestModeHintLabel);
		JPanel bodyTypePanel = new JBPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		bodyTypePanel.setOpaque(false);
		bodyTypePanel.add(formatButton);
		bodyTypePanel.add(Box.createHorizontalStrut(JBUI.scale(6)));
		bodyTypePanel.add(templateButton);
		bodyTypePanel.add(Box.createHorizontalStrut(JBUI.scale(12)));
		JLabel bodyTypeLabel = new JLabel("Body");
		bodyTypeLabel.setForeground(JBColor.GRAY);
		bodyTypePanel.add(bodyTypeLabel);
		bodyTypePanel.add(Box.createHorizontalStrut(JBUI.scale(6)));
		bodyTypePanel.add(bodyTypeField);
		footerPanel.add(infoPanel, BorderLayout.WEST);
		footerPanel.add(bodyTypePanel, BorderLayout.EAST);
		headerPanel.add(footerPanel, BorderLayout.SOUTH);

		this.setBorder(BorderFactory.createEmptyBorder());
		this.setLayout(new BorderLayout(0, JBUI.scale(8)));
		this.add(headerPanel, BorderLayout.NORTH);
		this.add(requestTabbedPane, BorderLayout.CENTER);
	}

	private void bindSendButtonActionListener() {
		sendButton.addActionListener(e -> {
			setRequestInFlight(true);
			setResponseMeta("Sending request...", JBColor.GRAY);
			ProgressManager.getInstance().run(new Task.Backgroundable(null, "Sending Request") {
				@Override
				public void run(@NotNull ProgressIndicator indicator) {
					String resolvedUrl = buildResolvedUrl();
					String method = getSelectedMethod();
					RequestHelper.RequestResult response = executeRequest(resolvedUrl, method);
					ToolkitUtil.invokeLater(() -> {
						addResponseTabPanel(response.body());
						setResponseMeta(formatResponseMeta(response), response.success()
								? new JBColor(new Color(0x5FAF5F), new Color(0x7BC67B))
								: new JBColor(new Color(0xB54A4A), new Color(0xE06C75)));
						setRequestInFlight(false);
					});
				}
			});

		});
	}

	private void bindUrlTextActionListener() {
		requestTabbedPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				super.mouseClicked(e);
				urlField.selectAll();
			}
		});

	}

	public void addRequestParamsTab(String requestParams) {
		StringBuilder paramBuilder = new StringBuilder();

		if (StringUtils.isNotBlank(requestParams)) {
			String[] paramArray = requestParams.split("&");
			for (String paramPairStr : paramArray) {
				String[] paramPair = paramPairStr.split("=");

				String param = paramPair[0];
				String value = paramPairStr.substring(param.length() + 1);
				paramBuilder.append(param).append(" : ").append(value).append("\n");
			}
		}

		String editorText = withTemplateFallback(paramBuilder.toString(), QUERY_TEMPLATE);
		if (requestParamsTextArea == null) {
			requestParamsTextArea = createTextArea(editorText, SyntaxConstants.SYNTAX_STYLE_NONE);
		} else {
			requestParamsTextArea.setText(editorText);
		}

		addRequestTabbedPane(QUERY_TAB_TITLE, requestParamsTextArea);
	}

	public void addRequestHeadersTab(String requestHeaders) {
		String editorText = withTemplateFallback(requestHeaders, HEADERS_TEMPLATE);
		if (requestHeadersTextArea == null) {
			requestHeadersTextArea = createTextArea(editorText, SyntaxConstants.SYNTAX_STYLE_NONE);
		} else {
			requestHeadersTextArea.setText(editorText);
		}

		addRequestTabbedPane(HEADERS_TAB_TITLE, requestHeadersTextArea);
	}

	public void addRequestBodyTabPanel(String text) {
		String reqBodyTitle = currentBodyTabTitle();
		String editorText = withTemplateFallback(text, defaultBodyTemplate());
		if (requestBodyTextArea == null) {
			requestBodyTextArea = createTextArea(editorText, SyntaxConstants.SYNTAX_STYLE_NONE);
		} else {
			requestBodyTextArea.setText(editorText);
		}
		syncBodyEditorMode();
		addRequestTabbedPane(reqBodyTitle, this.requestBodyTextArea);
	}

	public void addRequestTabbedPane(String title, RSyntaxTextArea jTextArea) {

		if (!JBColor.isBright()) {
			jTextArea.setBackground(new Color(0x2B2B2B));
			jTextArea.setForeground(new Color(0xBBBBBB));

			jTextArea.setSelectionColor(new Color(0x28437F));
			jTextArea.setCurrentLineHighlightColor(new Color(0x323232));
		} else {
			jTextArea.setBackground(new Color(0xFFFFFF));
			jTextArea.setForeground(new Color(0x000000));
			jTextArea.setSelectionColor(new Color(0xA6D2FF));
			jTextArea.setCurrentLineHighlightColor(new Color(0xFCFAED));
		}

		RTextScrollPane jbScrollPane = new RTextScrollPane(jTextArea);
		jTextArea.addKeyListener(new TextAreaKeyAdapter(jTextArea));
		upsertTab(title, jbScrollPane);
	}

	public void addResponseTabPanel(String text) {
		//FIXME RSyntaxTextArea 中文乱码
		String responseTabTitle = RESPONSE_TAB_TITLE;
		if (responseTextArea == null) {
			responseTextArea = createTextArea(text, SyntaxConstants.SYNTAX_STYLE_JSON);
			addRequestTabbedPane(responseTabTitle, responseTextArea);
		} else {
			responseTextArea.setText(text);
			addRequestTabbedPane(responseTabTitle, responseTextArea);
		}
	}

	@NotNull
	public RSyntaxTextArea createTextArea(String text, String style) {
		Font font = getTextAreaFont();

		RSyntaxTextArea jTextArea = new RSyntaxTextArea(text);
		jTextArea.setFont(font);
		jTextArea.setSyntaxEditingStyle(style);
		jTextArea.setCodeFoldingEnabled(true);

		jTextArea.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				String text = jTextArea.getText();
				getEffectiveFont(text);
			}
		});

		jTextArea.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() > 1) {
					CopyPasteManager.getInstance().setContents(new StringSelection(jTextArea.getText()));
				}
			}
		});
		return jTextArea;
	}

	public Font getTextAreaFont() {
		if (SystemInfo.isWindows) {
			return new Font("宋体", 0, 14);
		}
		if (SystemInfoRt.isMac) {
			return new Font("Menlo", 0, 14);
		}
		return new Font("Monospaced", 0, 14);
	}

	@NotNull
	private Font getEffectiveFont(String text) {
		FontPreferences fontPreferences = this.getFontPreferences();
		List<String> effectiveFontFamilies = fontPreferences.getEffectiveFontFamilies();

		int size = fontPreferences.getSize(fontPreferences.getFontFamily());
		Font font = new Font(FontPreferences.DEFAULT_FONT_NAME, Font.PLAIN, size);
		for (String effectiveFontFamily : effectiveFontFamilies) {
			Font effectiveFont = new Font(effectiveFontFamily, Font.PLAIN, size);
			if (effectiveFont.canDisplayUpTo(text) == -1) {
				font = effectiveFont;
				break;
			}
		}
		return font;
	}

	@NotNull
	private final FontPreferences getFontPreferences() {
		return new FontPreferences();
	}

	@NotNull
	private Font getEffectiveFont() {
		FontPreferences fontPreferences = this.getFontPreferences();
		String fontFamily = fontPreferences.getFontFamily();
		int size = fontPreferences.getSize(fontFamily);
		return new Font(FontPreferences.DEFAULT_FONT_NAME, Font.PLAIN, size);
	}

	public void resetRequestTabbedPane() {
		this.requestTabbedPane.removeAll();
		resetTextComponent(requestParamsTextArea);
		resetTextComponent(requestHeadersTextArea);
		resetTextComponent(requestBodyTextArea);
		resetTextComponent(responseTextArea);
	}

	private void resetTextComponent(JTextArea textComponent) {
		if (textComponent != null && StringUtils.isNotBlank(textComponent.getText())) {
			textComponent.setText("");
		}
	}

	public void setMethodValue(String method) {
		String methodText = String.valueOf(method);
		methodField.setSelectedItem(methodText);
		updateRequestModeHint();
	}

	public void setUrlValue(String url) {
		urlField.setText(url);
	}

	public void setEndpointSummary(String text) {
		endpointSummaryLabel.setText(StringUtils.defaultIfBlank(text, "Select an endpoint to inspect and test"));
	}

	public void setSourceValue(String text) {
		sourceInfoLabel.setText(StringUtils.defaultIfBlank(text, "Source unavailable"));
	}

	public void clearResponseMeta() {
		setResponseMeta("No request sent yet", JBColor.GRAY);
	}

	public void setResponseMeta(String text) {
		setResponseMeta(text, JBColor.GRAY);
	}

	private void setResponseMeta(String text, Color color) {
		responseMetaLabel.setForeground(color);
		responseMetaLabel.setText(StringUtils.defaultIfBlank(text, "No request sent yet"));
	}

	private void setRequestInFlight(boolean inFlight) {
		sendButton.setEnabled(!inFlight);
		sendButton.setText(inFlight ? "Sending..." : "Send");
	}

	private String buildResolvedUrl() {
		String url = urlField.getText();

		if (requestParamsTextArea != null) {
			String requestParamsText = requestParamsTextArea.getText();
			Map<String, String> paramMap = ToolkitUtil.textToParamMap(requestParamsText);
			if (!paramMap.isEmpty()) {
				for (String key : paramMap.keySet()) {
					url = url.replaceFirst("\\{(" + key + "[\\s\\S]*?)}", paramMap.get(key));
				}
			}

			String params = ToolkitUtil.textToRequestParam(requestParamsText);
			if (!params.isEmpty()) {
				if (url.contains("?")) {
					url += "&" + params;
				} else {
					url += "?" + params;
				}
			}
		}

		return url;
	}

	private RequestHelper.RequestResult executeRequest(String url, String method) {
		Map<String, String> headers = getEditableHeaders();
		String bodyType = getSelectedBodyType();
		String bodyText = getMeaningfulEditorText(requestBodyTextArea);
		if (StringUtils.isNotBlank(bodyText)) {
			if (BODY_TYPE_JSON.equals(bodyType)) {
				return RequestHelper.requestWithJsonBodyForResult(url, method, bodyText, headers);
			}
			if (BODY_TYPE_FORM.equals(bodyType)) {
				return RequestHelper.requestWithFormBodyForResult(url, method, ToolkitUtil.textToParamMap(bodyText), headers);
			}
		}
		return RequestHelper.requestForResult(url, method, headers);
	}

	private String getSelectedMethod() {
		Object selectedItem = methodField.getSelectedItem();
		return selectedItem == null ? HttpMethod.GET.name() : selectedItem.toString();
	}

	private String getSelectedBodyType() {
		Object selectedItem = bodyTypeField.getSelectedItem();
		return selectedItem == null ? BODY_TYPE_NONE : selectedItem.toString();
	}

	public void setBodyTypeValue(String bodyType) {
		bodyTypeField.setSelectedItem(StringUtils.defaultIfBlank(bodyType, BODY_TYPE_NONE));
		syncBodyEditorMode();
	}

	private void syncBodyEditorMode() {
		if (requestBodyTextArea == null) {
			updateRequestModeHint();
			updateBodyTabTitle();
			return;
		}
		String bodyType = getSelectedBodyType();
		if (BODY_TYPE_JSON.equals(bodyType)) {
			requestBodyTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
		} else {
			requestBodyTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
		}
		if (!hasMeaningfulContent(requestBodyTextArea) || isKnownTemplate(requestBodyTextArea.getText())) {
			requestBodyTextArea.setText(defaultBodyTemplate());
		}
		updateRequestModeHint();
		updateBodyTabTitle();
		if (!BODY_TYPE_NONE.equals(bodyType)) {
			int tabIndex = requestTabbedPane.indexOfTab(currentBodyTabTitle());
			if (tabIndex >= 0) {
				requestTabbedPane.setSelectedIndex(tabIndex);
			}
		}
	}

	private Map<String, String> getEditableHeaders() {
		if (requestHeadersTextArea == null) {
			return Map.of();
		}
		return ToolkitUtil.textToParamMap(getMeaningfulEditorText(requestHeadersTextArea));
	}

	private String formatResponseMeta(RequestHelper.RequestResult result) {
		String statusPart = result.statusCode() > 0
				? result.statusCode() + " " + result.statusText()
				: result.statusText();
		return statusPart + "  |  " + result.elapsedMillis() + " ms  |  " + formatBytes(result.responseBytes());
	}

	private void formatSelectedEditor() {
		RSyntaxTextArea editor = getSelectedRequestEditor();
		if (editor == null) {
			return;
		}
		if (editor == requestBodyTextArea && BODY_TYPE_JSON.equals(getSelectedBodyType())) {
			String text = getMeaningfulEditorText(editor);
			if (JsonUtils.isValidJson(text)) {
				JsonElement parse = JsonParser.parseString(text);
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				editor.setText(gson.toJson(parse));
			}
			return;
		}
		editor.setText(formatKeyValueText(editor.getText()));
	}

	private void applyTemplateForSelectedTab() {
		RSyntaxTextArea editor = getSelectedRequestEditor();
		if (editor == null) {
			return;
		}
		if (editor == requestParamsTextArea) {
			editor.setText(QUERY_TEMPLATE);
			return;
		}
		if (editor == requestHeadersTextArea) {
			editor.setText(HEADERS_TEMPLATE);
			return;
		}
		if (editor == requestBodyTextArea) {
			editor.setText(defaultBodyTemplate());
		}
	}

	private void updateRequestModeHint() {
		String method = getSelectedMethod();
		String bodyType = getSelectedBodyType();
		boolean methodUsuallyHasBody = Arrays.asList(HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name()).contains(method);

		if (!methodUsuallyHasBody) {
			requestModeHintLabel.setForeground(JBColor.GRAY);
			requestModeHintLabel.setText("Query / Path values are appended to the URL. Body is typically unused for " + method + ".");
			return;
		}

		if (BODY_TYPE_JSON.equals(bodyType)) {
			requestModeHintLabel.setForeground(JBColor.GRAY);
			requestModeHintLabel.setText("Body tab sends raw JSON. Query / Path values still stay in the URL.");
			return;
		}

		if (BODY_TYPE_FORM.equals(bodyType)) {
			requestModeHintLabel.setForeground(JBColor.GRAY);
			requestModeHintLabel.setText("Body tab sends x-www-form-urlencoded fields using `key : value`.");
			return;
		}

		requestModeHintLabel.setForeground(new JBColor(new Color(0x9A6700), new Color(0xE5C07B)));
		requestModeHintLabel.setText("Body is disabled. To send form fields or JSON, change Body from NONE.");
	}

	private String defaultBodyTemplate() {
		String bodyType = getSelectedBodyType();
		if (BODY_TYPE_JSON.equals(bodyType)) {
			return BODY_JSON_TEMPLATE;
		}
		if (BODY_TYPE_FORM.equals(bodyType)) {
			return BODY_FORM_TEMPLATE;
		}
		return BODY_NONE_TEMPLATE;
	}

	private String withTemplateFallback(String text, String template) {
		return StringUtils.isBlank(text) ? template : text;
	}

	private boolean hasMeaningfulContent(RSyntaxTextArea textArea) {
		return StringUtils.isNotBlank(getMeaningfulEditorText(textArea));
	}

	private String getMeaningfulEditorText(RSyntaxTextArea textArea) {
		if (textArea == null || StringUtils.isBlank(textArea.getText())) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		String[] lines = textArea.getText().split("\n");
		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.startsWith("//") || trimmed.isEmpty()) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append("\n");
			}
			builder.append(line);
		}
		return builder.toString().trim();
	}

	private boolean isKnownTemplate(String text) {
		return BODY_NONE_TEMPLATE.equals(text)
				|| BODY_JSON_TEMPLATE.equals(text)
				|| BODY_FORM_TEMPLATE.equals(text)
				|| QUERY_TEMPLATE.equals(text)
				|| HEADERS_TEMPLATE.equals(text);
	}

	private RSyntaxTextArea getSelectedRequestEditor() {
		Component selectedComponent = requestTabbedPane.getSelectedComponent();
		if (!(selectedComponent instanceof RTextScrollPane scrollPane)) {
			return null;
		}
		Component textArea = scrollPane.getTextArea();
		return textArea instanceof RSyntaxTextArea syntaxTextArea ? syntaxTextArea : null;
	}

	private String currentBodyTabTitle() {
		String bodyType = getSelectedBodyType();
		if (BODY_TYPE_JSON.equals(bodyType)) {
			return JSON_BODY_TAB_TITLE;
		}
		if (BODY_TYPE_FORM.equals(bodyType)) {
			return FORM_BODY_TAB_TITLE;
		}
		return BODY_TAB_TITLE;
	}

	private void updateBodyTabTitle() {
		renameTab(BODY_TAB_TITLE, currentBodyTabTitle());
		renameTab(JSON_BODY_TAB_TITLE, currentBodyTabTitle());
		renameTab(FORM_BODY_TAB_TITLE, currentBodyTabTitle());
	}

	private void renameTab(String oldTitle, String newTitle) {
		int index = requestTabbedPane.indexOfTab(oldTitle);
		if (index >= 0) {
			requestTabbedPane.setTitleAt(index, newTitle);
		}
	}

	private String formatKeyValueText(String text) {
		String meaningfulText = getMeaningfulEditorText(createDetachedTextArea(text));
		if (meaningfulText.isEmpty()) {
			return text;
		}
		StringBuilder builder = new StringBuilder();
		for (String line : meaningfulText.split("\n")) {
			if (!line.contains(":")) {
				if (builder.length() > 0) {
					builder.append("\n");
				}
				builder.append(line.trim());
				continue;
			}
			String[] parts = line.split(":", 2);
			if (builder.length() > 0) {
				builder.append("\n");
			}
			builder.append(parts[0].trim()).append(" : ").append(parts[1].trim());
		}
		return builder.toString();
	}

	private RSyntaxTextArea createDetachedTextArea(String text) {
		RSyntaxTextArea textArea = new RSyntaxTextArea(text);
		return textArea;
	}

	private String formatBytes(long byteCount) {
		if (byteCount < 1024) {
			return byteCount + " B";
		}
		if (byteCount < 1024 * 1024) {
			return String.format("%.1f KB", byteCount / 1024.0);
		}
		return String.format("%.1f MB", byteCount / (1024.0 * 1024.0));
	}

	private void upsertTab(String title, Component component) {
		int existingIndex = requestTabbedPane.indexOfTab(title);
		if (existingIndex >= 0) {
			requestTabbedPane.setComponentAt(existingIndex, component);
			requestTabbedPane.setSelectedIndex(existingIndex);
			return;
		}
		requestTabbedPane.addTab(title, component);
		requestTabbedPane.setSelectedComponent(component);
	}

	private class TextAreaKeyAdapter extends KeyAdapter {

		private final JTextArea jTextArea;

		public TextAreaKeyAdapter(JTextArea jTextArea) {
			this.jTextArea = jTextArea;
		}

		@Override
		public void keyPressed(KeyEvent event) {
			super.keyPressed(event);
			if ((event.getKeyCode() == KeyEvent.VK_ENTER)
					&& (event.isControlDown() || event.isMetaDown())) {
				String oldValue = jTextArea.getText();
				if (!JsonUtils.isValidJson(oldValue)) {
					return;
				}
				JsonElement parse = JsonParser.parseString(oldValue);
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				String json = gson.toJson(parse);
				jTextArea.setText(json);
			}
		}

	}

}
