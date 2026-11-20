package com.grumbo.ui;

import com.grumbo.simulation.Property;
import com.grumbo.simulation.Settings;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Separate window for the live simulation settings. Writes the same {@link Settings}
 * values the compute shaders read each step. The simulation window keeps the OpenGL context.
 */
public final class LiveSettingsWindow {

    private static Stage stage;
    private static final List<Refresher> refreshers = new ArrayList<>();
    private static boolean refreshing;
    private static final AnimationTimer timer = new AnimationTimer() {
        private long last;

        @Override
        public void handle(long now) {
            if (now - last < 200_000_000L) {
                return;
            }
            last = now;
            for (Refresher refresher : refreshers) {
                refresher.refresh();
            }
        }
    };

    private LiveSettingsWindow() {}

    /** Shows the window. Safe to call from the simulation thread. */
    public static void open() {
        SetupScreen.startToolkit();
        Platform.runLater(LiveSettingsWindow::show);
    }

    /** Hides the window without stopping JavaFX, so setup can open again. */
    public static void close() {
        if (Platform.isFxApplicationThread()) {
            hide();
        } else {
            Platform.runLater(LiveSettingsWindow::hide);
        }
    }

    /** Shows the window, or hides it when it is already open. */
    public static void toggle() {
        SetupScreen.startToolkit();
        Platform.runLater(() -> {
            if (stage != null && stage.isShowing()) {
                hide();
            } else {
                show();
            }
        });
    }

    private static void show() {
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("GravityChunk — Settings");
            VBox rows = new VBox(8);
            rows.setPadding(new Insets(12));
            for (String name : Settings.getInstance().getPropertyNames()) {
                Property<?> property = Settings.getInstance().getProperty(name);
                if (property == null || !property.isEditable()) {
                    continue;
                }
                rows.getChildren().add(row(property));
            }
            rows.setStyle("-fx-background-color: #14171f;");
            ScrollPane scroll = new ScrollPane(rows);
            scroll.setFitToWidth(true);
            scroll.setStyle("-fx-background-color: #14171f; -fx-background: #14171f;");
            Scene scene = new Scene(scroll, 520, 720);
            var css = LiveSettingsWindow.class.getResource("/ui/setup.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
            stage.setScene(scene);
            scroll.applyCss();
            scroll.layout();
            var viewport = scroll.lookup(".viewport");
            if (viewport != null) {
                viewport.setStyle("-fx-background-color: #14171f;");
            }
            stage.setOnCloseRequest(event -> {
                event.consume();
                hide();
            });
        }
        stage.show();
        stage.toFront();
        timer.start();
    }

    private static void hide() {
        timer.stop();
        if (stage != null) {
            stage.hide();
        }
    }

    private static HBox row(Property<?> property) {
        Label name = new Label(property.getName());
        name.setMinWidth(160);
        name.setWrapText(true);
        HBox box = new HBox(8, name);
        box.setFillHeight(true);
        switch (property.getTypeName()) {
            case "BOOLEAN" -> box.getChildren().add(check(property));
            case "SELECTOR" -> box.getChildren().add(selector(property));
            case "INT", "FLOAT", "DOUBLE" -> box.getChildren().add(number(property));
            default -> box.getChildren().add(text(property));
        }
        return box;
    }

    private static CheckBox check(Property<?> property) {
        CheckBox box = new CheckBox();
        box.setSelected(Boolean.TRUE.equals(property.getValue()));
        box.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (!refreshing) {
                commit(property.getName(), selected);
            }
        });
        refreshers.add(() -> {
            if (box.isFocused()) {
                return;
            }
            boolean value = Boolean.TRUE.equals(property.getValue());
            if (box.isSelected() != value) {
                refreshing = true;
                box.setSelected(value);
                refreshing = false;
            }
        });
        return box;
    }

    private static ComboBox<String> selector(Property<?> property) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll(property.getOptions());
        combo.setValue(String.valueOf(property.getValue()));
        combo.setOnAction(event -> {
            if (refreshing || combo.getValue() == null) {
                return;
            }
            int index = combo.getItems().indexOf(combo.getValue());
            if (index >= 0) {
                property.setSelectedIndex(index);
            }
            if ("renderMode".equals(property.getName())) {
                Settings.getInstance().selectRenderMode(combo.getValue());
            } else {
                commit(property.getName(), combo.getValue());
            }
        });
        refreshers.add(() -> {
            if (combo.isFocused() || combo.isShowing()) {
                return;
            }
            String value = String.valueOf(property.getValue());
            if (!value.equals(combo.getValue())) {
                refreshing = true;
                combo.setValue(value);
                refreshing = false;
            }
        });
        return combo;
    }

    private static HBox number(Property<?> property) {
        TextField field = new TextField(textOf(property.getValue()));
        field.setPrefColumnCount(8);
        field.setOnAction(event -> commitNumber(property, field));
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                commitNumber(property, field);
            }
        });
        Button minus = new Button(property.getTypeName().equals("INT") ? "-" : "/");
        Button plus = new Button(property.getTypeName().equals("INT") ? "+" : "*");
        minus.setOnAction(event -> nudge(property, false));
        plus.setOnAction(event -> nudge(property, true));
        HBox box = new HBox(6, field, minus, plus);
        HBox.setHgrow(field, Priority.ALWAYS);
        if (property.hasRange()) {
            Slider slider = new Slider(property.getMinAsDouble(), property.getMaxAsDouble(), numberValue(property));
            slider.setPrefWidth(120);
            slider.valueProperty().addListener((obs, oldValue, value) -> {
                if (!refreshing) {
                    applyNumberValue(property, value.doubleValue());
                }
            });
            slider.setOnMouseReleased(event -> {
                applyNumberValue(property, slider.getValue());
                Settings.getInstance().saveSettings();
            });
            box.getChildren().add(slider);
            refreshers.add(() -> {
                if (slider.isValueChanging()) {
                    return;
                }
                double value = numberValue(property);
                if (Math.abs(slider.getValue() - value) > 1e-6) {
                    refreshing = true;
                    slider.setValue(value);
                    refreshing = false;
                }
            });
        }
        refreshers.add(() -> {
            if (field.isFocused()) {
                return;
            }
            String text = textOf(property.getValue());
            if (!text.equals(field.getText())) {
                field.setText(text);
            }
        });
        return box;
    }

    private static TextField text(Property<?> property) {
        TextField field = new TextField(textOf(property.getValue()));
        field.setPrefColumnCount(16);
        field.setOnAction(event -> commitText(property, field));
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                commitText(property, field);
            }
        });
        refreshers.add(() -> {
            if (field.isFocused()) {
                return;
            }
            String text = textOf(property.getValue());
            if (!text.equals(field.getText())) {
                field.setText(text);
            }
        });
        return field;
    }

    private static void nudge(Property<?> property, boolean up) {
        String type = property.getTypeName();
        if ("INT".equals(type)) {
            int value = ((Number) property.getValue()).intValue();
            commit(property.getName(), up ? value + 1 : value - 1);
        } else if ("DOUBLE".equals(type)) {
            double value = ((Number) property.getValue()).doubleValue();
            commit(property.getName(), up ? value * 1.1 : value / 1.1);
        } else {
            float value = ((Number) property.getValue()).floatValue();
            commit(property.getName(), up ? value * 1.1f : value / 1.1f);
        }
    }

    private static void commitNumber(Property<?> property, TextField field) {
        try {
            String type = property.getTypeName();
            if ("INT".equals(type)) {
                commit(property.getName(), Integer.parseInt(field.getText().trim()));
            } else if ("DOUBLE".equals(type)) {
                commit(property.getName(), Double.parseDouble(field.getText().trim()));
            } else {
                commit(property.getName(), Float.parseFloat(field.getText().trim()));
            }
        } catch (RuntimeException ignored) {
            field.setText(textOf(property.getValue()));
        }
    }

    private static void applyNumberValue(Property<?> property, double value) {
        try {
            String type = property.getTypeName();
            if ("INT".equals(type)) {
                apply(property.getName(), (int) Math.round(value));
            } else if ("DOUBLE".equals(type)) {
                apply(property.getName(), value);
            } else {
                apply(property.getName(), (float) value);
            }
        } catch (RuntimeException ignored) {
            // Out of range. The slider is clamped, and the field refresh restores the kept value.
        }
    }

    private static void commitText(Property<?> property, TextField field) {
        try {
            switch (property.getTypeName()) {
                case "VECTOR3F" -> {
                    String[] parts = field.getText().trim().split("[,\\s]+");
                    if (parts.length < 3) {
                        throw new IllegalArgumentException("need x y z");
                    }
                    commit(property.getName(), new Vector3f(
                        Float.parseFloat(parts[0]), Float.parseFloat(parts[1]), Float.parseFloat(parts[2])));
                }
                case "DOUBLE_ARRAY" -> {
                    String raw = field.getText().trim().replace("[", "").replace("]", "");
                    String[] parts = raw.split("[,\\s]+");
                    double[] values = new double[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        values[i] = Double.parseDouble(parts[i]);
                    }
                    commit(property.getName(), values);
                }
                case "COLOR" -> {
                    String[] parts = field.getText().trim().split("[,\\s]+");
                    if (parts.length < 3) {
                        throw new IllegalArgumentException("need r g b");
                    }
                    commit(property.getName(), new Color(
                        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                }
                default -> commit(property.getName(), field.getText());
            }
        } catch (RuntimeException ignored) {
            field.setText(textOf(property.getValue()));
        }
    }

    private static void apply(String name, Object value) {
        Settings.getInstance().setValue(name, value);
    }

    private static void commit(String name, Object value) {
        try {
            apply(name, value);
            Settings.getInstance().saveSettings();
        } catch (RuntimeException ignored) {
            // The next refresh puts the field back to the value Settings kept.
        }
    }

    private static double numberValue(Property<?> property) {
        Object value = property.getValue();
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }

    private static String textOf(Object value) {
        if (value instanceof Vector3f vector) {
            return vector.x + ", " + vector.y + ", " + vector.z;
        }
        if (value instanceof double[] array) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(array[i]);
            }
            return text.toString();
        }
        if (value instanceof Color color) {
            return color.getRed() + ", " + color.getGreen() + ", " + color.getBlue();
        }
        return value == null ? "" : String.valueOf(value);
    }

    private interface Refresher {
        void refresh();
    }
}
