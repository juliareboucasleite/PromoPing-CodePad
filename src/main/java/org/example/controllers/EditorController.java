package org.example.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.geometry.Point2D;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.reactfx.Subscription;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorController {

    private static final String THEME_LIGHT = "/org/example/editor-light.css";
    private static final String THEME_DARK = "/org/example/editor-dark.css";
    private static final String[] KEYWORDS = new String[]{
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while"
    };

    private static final String[] KEYWORDS_JS = new String[]{
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "export", "extends", "finally", "for", "function", "if",
            "import", "in", "instanceof", "let", "new", "return", "super", "switch", "this",
            "throw", "try", "typeof", "var", "void", "while", "with", "yield", "await"
    };

    private static final String[] KEYWORDS_PY = new String[]{
            "and", "as", "assert", "break", "class", "continue", "def", "del", "elif", "else",
            "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return", "True",
            "try", "while", "with", "yield"
    };

    private static final Pattern PATTERN_JAVA = buildPattern(KEYWORDS,
            "//[^\\n]*|/\\*(.|\\R)*?\\*/",
            "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'");
    private static final Pattern PATTERN_JS = buildPattern(KEYWORDS_JS,
            "//[^\\n]*|/\\*(.|\\R)*?\\*/",
            "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'|`([^`\\\\]|\\\\.)*`");
    private static final Pattern PATTERN_PY = buildPattern(KEYWORDS_PY,
            "#[^\\n]*",
            "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'");

    private static final String CURSOR = "${cursor}";
    private static final Map<String, String> SNIPPETS = new LinkedHashMap<>();

    static {
        SNIPPETS.put("psvm", "public static void main(String[] args) {\n    " + CURSOR + "\n}");
        SNIPPETS.put("sout", "System.out.println(" + CURSOR + ");");
        SNIPPETS.put("fori", "for (int i = 0; i < " + CURSOR + "; i++) {\n    \n}");
        SNIPPETS.put("if", "if (" + CURSOR + ") {\n    \n}");
    }

    @FXML
    private BorderPane root;
    @FXML
    private TabPane tabPane;
    @FXML
    private Label lblStatus;
    @FXML
    private Label lblStats;
    @FXML
    private Button btnRun;

    @FXML
    private MenuItem miNewTab;
    @FXML
    private MenuItem miOpen;
    @FXML
    private MenuItem miSave;
    @FXML
    private MenuItem miSaveAs;
    @FXML
    private MenuItem miCloseTab;
    @FXML
    private MenuItem miExit;

    @FXML
    private MenuItem miUndo;
    @FXML
    private MenuItem miRedo;
    @FXML
    private MenuItem miCut;
    @FXML
    private MenuItem miCopy;
    @FXML
    private MenuItem miPaste;
    @FXML
    private MenuItem miSelectAll;
    @FXML
    private MenuItem miFind;
    @FXML
    private MenuItem miReplace;

    @FXML
    private RadioMenuItem miThemeLight;
    @FXML
    private RadioMenuItem miThemeDark;
    @FXML
    private RadioMenuItem miModeText;
    @FXML
    private RadioMenuItem miModeCode;

    @FXML
    private MenuItem miAbout;

    private int untitledCount = 1;
    private String currentTheme = THEME_LIGHT;
    private Stage findStage;
    private TextField tfFind;
    private TextField tfReplace;
    private Label lblFindStatus;
    private ContextMenu suggestMenu;

    private static class TabData {
        CodeArea area;
        Path filePath;
        boolean dirty;
        boolean loading;
        Subscription highlightSubscription;
        boolean codeMode;
        String language;
        Pattern pattern;
    }

    @FXML
    public void initialize() {
        ToggleGroup themeGroup = new ToggleGroup();
        miThemeLight.setToggleGroup(themeGroup);
        miThemeDark.setToggleGroup(themeGroup);
        miThemeLight.setSelected(true);

        ToggleGroup modeGroup = new ToggleGroup();
        miModeText.setToggleGroup(modeGroup);
        miModeCode.setToggleGroup(modeGroup);
        miModeCode.setSelected(true);

        setupShortcuts();
        createNewTab();
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateStatus("Pronto");
            syncModeToggle(newTab);
            updateStats();
        });
    }

    private void setupShortcuts() {
        miNewTab.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.N, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miOpen.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.O, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miSave.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.S, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miSaveAs.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.S, javafx.scene.input.KeyCombination.CONTROL_DOWN,
                javafx.scene.input.KeyCombination.SHIFT_DOWN));
        miFind.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.F, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miReplace.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.H, javafx.scene.input.KeyCombination.CONTROL_DOWN));
    }

    private void createNewTab() {
        String title = "Sem Titulo " + untitledCount++;
        Tab tab = new Tab(title);
        TabData data = buildCodeTab(tab, "");
        tab.setUserData(data);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        if (miModeText != null && miModeText.isSelected()) {
            setMode(data, false);
        }
        updateStats();
    }

    private TabData buildCodeTab(Tab tab, String content) {
        CodeArea area = new CodeArea();
        area.getStyleClass().add("code-area");
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));

        TabData data = new TabData();
        data.area = area;
        data.loading = true;
        area.replaceText(content == null ? "" : content);
        data.loading = false;
        data.dirty = false;
        data.codeMode = true;
        data.language = "java";
        data.pattern = PATTERN_JAVA;

        attachHighlight(data);

        area.plainTextChanges().subscribe(ignore -> {
            if (!data.loading) {
                markDirty(tab, true);
            }
            updateStats();
        });

        area.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCaretStatus(area));

        setupEditorInteractions(data);
        VirtualizedScrollPane<CodeArea> scroller = new VirtualizedScrollPane<>(area);
        tab.setContent(scroller);
        tab.setOnCloseRequest(event -> {
            if (!confirmClose(tab)) {
                event.consume();
            }
        });

        applyHighlight(data);
        return data;
    }

    private void applyHighlight(TabData data) {
        if (data == null || data.pattern == null) {
            return;
        }
        StyleSpans<Collection<String>> spans = computeHighlighting(data.area.getText(), data.pattern);
        data.area.setStyleSpans(0, spans);
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                            matcher.group("TYPE") != null ? "type" :
                                    matcher.group("FUNCTION") != null ? "function" :
                                            matcher.group("IDENT") != null ? "identifier" :
                            matcher.group("PAREN") != null ? "paren" :
                                    matcher.group("BRACE") != null ? "brace" :
                                            matcher.group("BRACKET") != null ? "bracket" :
                                                    matcher.group("SEMICOLON") != null ? "semicolon" :
                                                            matcher.group("STRING") != null ? "string" :
                                                                    matcher.group("COMMENT") != null ? "comment" :
                                                                            matcher.group("NUMBER") != null ? "number" :
                                                                                    null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private CodeArea getCurrentArea() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) {
            return null;
        }
        TabData data = (TabData) tab.getUserData();
        return data == null ? null : data.area;
    }

    private TabData getCurrentData() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) {
            return null;
        }
        return (TabData) tab.getUserData();
    }

    private void updateCaretStatus(CodeArea area) {
        int line = area.getCurrentParagraph() + 1;
        int col = area.getCaretColumn() + 1;
        updateStatus("Linha " + line + ", Coluna " + col);
    }

    private void updateStatus(String message) {
        if (lblStatus != null) {
            lblStatus.setText(message);
        }
    }

    private void updateStats() {
        if (lblStats == null) {
            return;
        }
        TabData data = getCurrentData();
        if (data == null) {
            lblStats.setText("Palavras: 0 | Caracteres: 0");
            return;
        }
        String text = data.area.getText();
        int chars = text.length();
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        lblStats.setText("Palavras: " + words + " | Caracteres: " + chars);
    }

    private void syncModeToggle(Tab tab) {
        if (tab == null) {
            return;
        }
        TabData data = (TabData) tab.getUserData();
        if (data == null) {
            return;
        }
        if (data.codeMode) {
            miModeCode.setSelected(true);
        } else {
            miModeText.setSelected(true);
        }
        updateRunButtonState(data);
    }

    private void attachHighlight(TabData data) {
        if (data.highlightSubscription != null) {
            data.highlightSubscription.unsubscribe();
        }
        data.highlightSubscription = data.area.multiPlainChanges()
                .successionEnds(Duration.ofMillis(200))
                .subscribe(ignore -> applyHighlight(data));
    }

    private void detachHighlight(TabData data) {
        if (data.highlightSubscription != null) {
            data.highlightSubscription.unsubscribe();
            data.highlightSubscription = null;
        }
    }

    private void clearStyles(CodeArea area) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        spansBuilder.add(Collections.emptyList(), area.getLength());
        area.setStyleSpans(0, spansBuilder.create());
    }

    private void setMode(TabData data, boolean codeMode) {
        data.codeMode = codeMode;
        data.area.getStyleClass().removeAll("code-area", "text-area");
        data.area.getStyleClass().add(codeMode ? "code-area" : "text-area");
        if (codeMode) {
            attachHighlight(data);
            applyHighlight(data);
        } else {
            detachHighlight(data);
            clearStyles(data.area);
        }
        updateRunButtonState(data);
    }

    private void updateRunButtonState(TabData data) {
        if (btnRun == null) {
            return;
        }
        btnRun.setDisable(data == null || !data.codeMode);
    }

    private void setupEditorInteractions(TabData data) {
        data.area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.SPACE) {
                showSuggestions(data);
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.TAB) {
                if (tryExpandSnippet(data)) {
                    event.consume();
                }
            }
        });

        data.area.addEventHandler(KeyEvent.KEY_TYPED, event -> {
            if (data.codeMode && event.getCharacter() != null && event.getCharacter().length() == 1) {
                char ch = event.getCharacter().charAt(0);
                if (handleAutoPair(data.area, ch)) {
                    event.consume();
                    return;
                }
            }
            if (suggestMenu != null && suggestMenu.isShowing()) {
                suggestMenu.hide();
            }
        });
    }

    private boolean handleAutoPair(CodeArea area, char ch) {
        String closing = switch (ch) {
            case '(' -> ")";
            case '[' -> "]";
            case '{' -> "}";
            case '"' -> "\"";
            case '\'' -> "'";
            default -> null;
        };
        if (closing == null) {
            return false;
        }
        String selected = area.getSelectedText();
        if (selected != null && !selected.isEmpty()) {
            area.replaceSelection(ch + selected + closing);
            area.moveTo(area.getCaretPosition() - closing.length());
            return true;
        }
        int caret = area.getCaretPosition();
        area.insertText(caret, String.valueOf(ch) + closing);
        area.moveTo(caret + 1);
        return true;
    }

    private boolean tryExpandSnippet(TabData data) {
        String word = getCurrentWord(data.area);
        if (word == null) {
            return false;
        }
        String snippet = SNIPPETS.get(word);
        if (snippet == null) {
            return false;
        }
        replaceCurrentWordWithSnippet(data.area, snippet);
        return true;
    }

    private void replaceCurrentWordWithSnippet(CodeArea area, String snippet) {
        int[] range = getCurrentWordRange(area);
        if (range == null) {
            return;
        }
        int start = range[0];
        int end = range[1];
        int cursorIndex = snippet.indexOf(CURSOR);
        String cleanSnippet = snippet.replace(CURSOR, "");
        area.replaceText(start, end, cleanSnippet);
        if (cursorIndex >= 0) {
            area.moveTo(start + cursorIndex);
        } else {
            area.moveTo(start + cleanSnippet.length());
        }
    }

    private void showSuggestions(TabData data) {
        if (suggestMenu == null) {
            suggestMenu = new ContextMenu();
        }
        suggestMenu.getItems().clear();
        String prefix = getCurrentWord(data.area);
        for (String suggestion : buildSuggestions(data, prefix)) {
            MenuItem item = new MenuItem(suggestion);
            item.setOnAction(e -> replaceCurrentWord(data.area, suggestion));
            suggestMenu.getItems().add(item);
        }
        if (suggestMenu.getItems().isEmpty()) {
            return;
        }
        data.area.getCaretBounds().ifPresentOrElse(bounds -> {
            Point2D point = data.area.localToScreen(bounds.getMaxX(), bounds.getMaxY());
            suggestMenu.show(data.area, point.getX(), point.getY());
        }, () -> suggestMenu.show(data.area, 0, 0));
    }

    private Set<String> buildSuggestions(TabData data, String prefix) {
        Set<String> result = new LinkedHashSet<>();
        String norm = prefix == null ? "" : prefix.trim();
        if (data.codeMode) {
            for (String key : getKeywordsForLanguage(data.language)) {
                if (norm.isEmpty() || key.startsWith(norm)) {
                    result.add(key);
                }
            }
        }
        for (String key : SNIPPETS.keySet()) {
            if (norm.isEmpty() || key.startsWith(norm)) {
                result.add(key);
            }
        }
        String text = data.area.getText();
        Matcher matcher = Pattern.compile("\\b[a-zA-Z_][\\w]*\\b").matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            if (norm.isEmpty() || word.startsWith(norm)) {
                result.add(word);
            }
            if (result.size() > 80) {
                break;
            }
        }
        return result;
    }

    private String getCurrentWord(CodeArea area) {
        int[] range = getCurrentWordRange(area);
        if (range == null) {
            return null;
        }
        return area.getText(range[0], range[1]);
    }

    private int[] getCurrentWordRange(CodeArea area) {
        int caret = area.getCaretPosition();
        String text = area.getText();
        if (text.isEmpty() || caret < 0) {
            return null;
        }
        int start = caret;
        int end = caret;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
            end++;
        }
        if (start == end) {
            return null;
        }
        return new int[]{start, end};
    }

    private void replaceCurrentWord(CodeArea area, String replacement) {
        int[] range = getCurrentWordRange(area);
        if (range == null) {
            return;
        }
        area.replaceText(range[0], range[1], replacement);
    }

    private void markDirty(Tab tab, boolean dirty) {
        TabData data = (TabData) tab.getUserData();
        if (data != null && data.dirty != dirty) {
            data.dirty = dirty;
            String baseName = tab.getText();
            if (baseName.endsWith("*")) {
                baseName = baseName.substring(0, baseName.length() - 1);
            }
            tab.setText(dirty ? baseName + "*" : baseName);
        }
    }

    private boolean confirmClose(Tab tab) {
        TabData data = (TabData) tab.getUserData();
        if (data == null || !data.dirty) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Alterações não salvas");
        alert.setHeaderText("Salvar alterações antes de fechar?");
        alert.setContentText(tab.getText().replace("*", ""));
        ButtonType btnSave = new ButtonType("Salvar");
        ButtonType btnDont = new ButtonType("Não salvar");
        ButtonType btnCancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnSave, btnDont, btnCancel);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == btnCancel) {
            return false;
        }
        if (result.get() == btnSave) {
            tabPane.getSelectionModel().select(tab);
            return handleSaveInternal(false);
        }
        return true;
    }

    private void setCurrentFile(TabData data, Tab tab, Path path) {
        data.filePath = path;
        String name = path == null ? "Sem Titulo" : path.getFileName().toString();
        tab.setText(name + (data.dirty ? "*" : ""));
    }

    @FXML
    public void handleNewTab() {
        createNewTab();
    }

    @FXML
    public void handleOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir arquivo");
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Tab tab = new Tab(file.getName());
            TabData data = buildCodeTab(tab, content);
            data.filePath = file.toPath();
            String language = detectLanguage(file.toPath());
            if ("text".equals(language)) {
                setMode(data, false);
            } else {
                data.language = language;
                data.pattern = patternForLanguage(language);
                applyHighlight(data);
            }
            tab.setUserData(data);
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            markDirty(tab, false);
            if (miModeText != null && miModeText.isSelected()) {
                setMode(data, false);
            }
            updateStatus("Arquivo aberto: " + file.getName());
            updateStats();
        } catch (IOException ex) {
            showError("Não foi possível abrir o arquivo.", ex.getMessage());
        }
    }

    @FXML
    public void handleSave() {
        handleSaveInternal(false);
    }

    @FXML
    public void handleSaveAs() {
        handleSaveInternal(true);
    }

    private boolean handleSaveInternal(boolean forceSaveAs) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        TabData data = getCurrentData();
        if (tab == null || data == null) {
            return false;
        }
        Path path = data.filePath;
        if (forceSaveAs || path == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Salvar arquivo");
            File file = chooser.showSaveDialog(root.getScene().getWindow());
            if (file == null) {
                return false;
            }
            path = file.toPath();
        }
        try {
            Files.writeString(path, data.area.getText(), StandardCharsets.UTF_8);
            data.filePath = path;
            markDirty(tab, false);
            setCurrentFile(data, tab, path);
            String language = detectLanguage(path);
            if (data.codeMode && !"text".equals(language)) {
                data.language = language;
                data.pattern = patternForLanguage(language);
                applyHighlight(data);
            }
            updateStatus("Arquivo salvo: " + path.getFileName());
            return true;
        } catch (IOException ex) {
            showError("Não foi possível salvar o arquivo.", ex.getMessage());
            return false;
        }
    }

    @FXML
    public void handleCloseTab() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab != null && confirmClose(tab)) {
            tabPane.getTabs().remove(tab);
            if (tabPane.getTabs().isEmpty()) {
                createNewTab();
            }
        }
    }

    @FXML
    public void handleExit() {
        for (Tab tab : tabPane.getTabs()) {
            if (!confirmClose(tab)) {
                return;
            }
        }
        Platform.exit();
    }

    @FXML
    public void handleUndo() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            area.undo();
        }
    }

    @FXML
    public void handleRedo() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            area.redo();
        }
    }

    @FXML
    public void handleCut() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(area.getSelectedText());
            Clipboard.getSystemClipboard().setContent(content);
            area.replaceSelection("");
        }
    }

    @FXML
    public void handleCopy() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(area.getSelectedText());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    @FXML
    public void handlePaste() {
        CodeArea area = getCurrentArea();
        if (area != null && Clipboard.getSystemClipboard().hasString()) {
            area.replaceSelection(Clipboard.getSystemClipboard().getString());
        }
    }

    @FXML
    public void handleSelectAll() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            area.selectAll();
        }
    }

    @FXML
    public void handleFind() {
        showFindReplace(false);
    }

    @FXML
    public void handleReplace() {
        showFindReplace(true);
    }

    @FXML
    public void handleModeText() {
        TabData data = getCurrentData();
        if (data != null) {
            setMode(data, false);
        }
    }

    @FXML
    public void handleModeCode() {
        TabData data = getCurrentData();
        if (data != null) {
            if (data.language == null) {
                data.language = "java";
                data.pattern = PATTERN_JAVA;
            }
            setMode(data, true);
        }
    }

    @FXML
    public void handleRun() {
        TabData data = getCurrentData();
        if (data == null || !data.codeMode) {
            return;
        }
        if (data.filePath == null || data.dirty) {
            boolean saved = handleSaveInternal(false);
            if (!saved) {
                return;
            }
        }
        String language = detectLanguage(data.filePath);
        runCodeAsync(data, language);
    }

    private void showFindReplace(boolean focusReplace) {
        if (findStage == null) {
            buildFindDialog();
        }
        findStage.show();
        findStage.toFront();
        if (focusReplace) {
            tfReplace.requestFocus();
        } else {
            tfFind.requestFocus();
        }
    }

    private void buildFindDialog() {
        findStage = new Stage();
        findStage.setTitle("Buscar e Substituir");
        findStage.initModality(Modality.NONE);
        findStage.initOwner(root.getScene().getWindow());

        tfFind = new TextField();
        tfReplace = new TextField();
        lblFindStatus = new Label();

        Button btnNext = new Button("Próximo");
        Button btnPrev = new Button("Anterior");
        Button btnReplace = new Button("Substituir");
        Button btnReplaceAll = new Button("Substituir Tudo");

        btnNext.setOnAction(e -> findNext(true));
        btnPrev.setOnAction(e -> findNext(false));
        btnReplace.setOnAction(e -> replaceOnce());
        btnReplaceAll.setOnAction(e -> replaceAll());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label("Buscar:"), 0, 0);
        grid.add(tfFind, 1, 0);
        grid.add(new Label("Substituir:"), 0, 1);
        grid.add(tfReplace, 1, 1);
        GridPane.setHgrow(tfFind, Priority.ALWAYS);
        GridPane.setHgrow(tfReplace, Priority.ALWAYS);

        HBox actions = new HBox(8, btnPrev, btnNext, btnReplace, btnReplaceAll);
        VBox rootBox = new VBox(10, grid, actions, lblFindStatus);
        rootBox.setStyle("-fx-padding: 12;");

        findStage.setScene(new Scene(rootBox, 420, 150));
    }

    private void findNext(boolean forward) {
        CodeArea area = getCurrentArea();
        if (area == null) {
            return;
        }
        String query = tfFind.getText();
        if (query == null || query.isEmpty()) {
            lblFindStatus.setText("Digite algo para buscar.");
            return;
        }
        String text = area.getText();
        int start = area.getCaretPosition();
        int index = forward ? text.indexOf(query, start) : text.lastIndexOf(query, Math.max(0, start - 1));
        if (index == -1 && start != 0) {
            index = forward ? text.indexOf(query) : text.lastIndexOf(query);
        }
        if (index == -1) {
            lblFindStatus.setText("Nenhuma ocorrência encontrada.");
            return;
        }
        area.selectRange(index, index + query.length());
        area.requestFollowCaret();
        lblFindStatus.setText("Ocorrência em " + (index + 1));
    }

    private void replaceOnce() {
        CodeArea area = getCurrentArea();
        if (area == null) {
            return;
        }
        String query = tfFind.getText();
        String replacement = tfReplace.getText();
        if (query == null || query.isEmpty()) {
            lblFindStatus.setText("Digite algo para buscar.");
            return;
        }
        if (area.getSelectedText().equals(query)) {
            area.replaceSelection(replacement == null ? "" : replacement);
        }
        findNext(true);
    }

    private void replaceAll() {
        CodeArea area = getCurrentArea();
        if (area == null) {
            return;
        }
        String query = tfFind.getText();
        String replacement = tfReplace.getText();
        if (query == null || query.isEmpty()) {
            lblFindStatus.setText("Digite algo para buscar.");
            return;
        }
        String text = area.getText();
        String replaced = text.replace(query, replacement == null ? "" : replacement);
        area.replaceText(replaced);
        lblFindStatus.setText("Substituição concluída.");
    }

    @FXML
    public void handleThemeLight() {
        switchTheme(THEME_LIGHT);
    }

    @FXML
    public void handleThemeDark() {
        switchTheme(THEME_DARK);
    }

    private void switchTheme(String theme) {
        if (root == null) {
            return;
        }
        root.getStylesheets().clear();
        java.net.URL url = getClass().getResource(theme);
        if (url == null) {
            showError("Tema não encontrado", "Não foi possível carregar " + theme);
            return;
        }
        root.getStylesheets().add(url.toExternalForm());
        currentTheme = theme;
    }

    @FXML
    public void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Sobre");
        alert.setHeaderText("PromoPing CodePad");
        alert.setContentText("Editor simples com abas, destaque de sintaxe e temas.");
        alert.showAndWait();
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private static Pattern buildPattern(String[] keywords, String commentRegex, String stringRegex) {
        String keywordPattern = "\\b(" + String.join("|", keywords) + ")\\b";
        String typePattern = "\\b[A-Z][\\w]*\\b";
        String functionPattern = "\\b[a-zA-Z_][\\w]*(?=\\s*\\()";
        String identPattern = "\\b[a-zA-Z_][\\w]*\\b";
        String numberPattern = "\\b\\d+(\\.\\d+)?\\b";
        return Pattern.compile(
                "(?<KEYWORD>" + keywordPattern + ")"
                        + "|(?<TYPE>" + typePattern + ")"
                        + "|(?<FUNCTION>" + functionPattern + ")"
                        + "|(?<IDENT>" + identPattern + ")"
                        + "|(?<PAREN>\\(|\\))"
                        + "|(?<BRACE>\\{|\\})"
                        + "|(?<BRACKET>\\[|\\])"
                        + "|(?<SEMICOLON>;)"
                        + "|(?<STRING>" + stringRegex + ")"
                        + "|(?<COMMENT>" + commentRegex + ")"
                        + "|(?<NUMBER>" + numberPattern + ")"
        );
    }

    private static String detectLanguage(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".java")) {
            return "java";
        }
        if (name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".ts")) {
            return "js";
        }
        if (name.endsWith(".py")) {
            return "py";
        }
        if (name.endsWith(".html") || name.endsWith(".css")) {
            return "text";
        }
        if (name.endsWith(".txt") || name.endsWith(".md")) {
            return "text";
        }
        return "java";
    }

    private static Pattern patternForLanguage(String language) {
        if ("js".equals(language)) {
            return PATTERN_JS;
        }
        if ("py".equals(language)) {
            return PATTERN_PY;
        }
        return PATTERN_JAVA;
    }

    private static String[] getKeywordsForLanguage(String language) {
        if ("js".equals(language)) {
            return KEYWORDS_JS;
        }
        if ("py".equals(language)) {
            return KEYWORDS_PY;
        }
        return KEYWORDS;
    }
}
