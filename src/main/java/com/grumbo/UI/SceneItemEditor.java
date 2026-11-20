package com.grumbo.ui;

import com.grumbo.simulation.Planet;
import com.grumbo.simulation.SimulationSetup.SceneItem;
import com.grumbo.simulation.UnitSet;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * Edits the generator inputs stored on one scene object.
 * Returns null when the user cancels or the object has no editable inputs.
 */
final class SceneItemEditor {

    private SceneItemEditor() {}

    static SceneItem edit(Window owner, SceneItem item, UnitSet units) {
        if (item == null) {
            return null;
        }
        return switch (item.type) {
            case DISK -> editDisk(owner, item);
            case BOX -> editBox(owner, item);
            case SINGLE -> editSingle(owner, item);
            case SPICE_KERNEL -> editSpice(owner, item, units);
            case SOLAR_SYSTEM -> {
                info(owner, item.label, String.join("\n",
                    "Bodies are read from solar_system.json when you launch.",
                    "The unit set is forced to solar-system hours.",
                    "Estimated bodies: 24.",
                    "This object has no placement or mass fields to edit."));
                yield null;
            }
        };
    }

    private static SceneItem editDisk(Window owner, SceneItem item) {
        TextField label = field(item.label);
        TextField count = field(Integer.toString(item.bodyCount));
        TextField cx = field(num(item.cx));
        TextField cy = field(num(item.cy));
        TextField cz = field(num(item.cz));
        TextField inner = field(num(item.innerRadius));
        TextField outer = field(num(item.radius));
        TextField massMin = field(num(item.massMin));
        TextField massMax = field(num(item.massMax));
        TextField densityMin = field(num(item.densityMin));
        TextField densityMax = field(num(item.densityMax));
        TextField centerMass = field(num(item.centerMass));
        TextField centerDensity = field(num(item.centerDensity));
        TextField nx = field(num(item.nx));
        TextField ny = field(num(item.ny));
        TextField nz = field(num(item.nz));
        TextField vx = field(num(item.vx));
        TextField vy = field(num(item.vy));
        TextField vz = field(num(item.vz));
        TextField adherence = field(num(item.adherence));
        TextField orbitalFactor = field(num(item.orbitalFactor));
        CheckBox ccw = new CheckBox("Counter-clockwise");
        ccw.setSelected(item.ccw);
        CheckBox orbital = new CheckBox("Give orbital velocity");
        orbital.setSelected(item.giveOrbitalVelocity);

        GridPane grid = new GridPane();
        int next = fill(grid,
            row("Label", label),
            row("Count", count),
            row("Center X", cx), row("Center Y", cy), row("Center Z", cz),
            row("Inner radius", inner), row("Outer radius", outer),
            row("Particle mass min", massMin), row("Particle mass max", massMax),
            row("Density min", densityMin), row("Density max", densityMax),
            row("Center mass", centerMass), row("Center density", centerDensity),
            row("Normal X", nx), row("Normal Y", ny), row("Normal Z", nz),
            row("Added velocity X", vx), row("Added velocity Y", vy), row("Added velocity Z", vz),
            row("Plane adherence", adherence), row("Orbital factor", orbitalFactor)
        );
        grid.add(ccw, 1, next);
        grid.add(orbital, 1, next + 1);

        return show(owner, item.label, grid, () -> {
            float innerRadius = f(inner, "Inner radius");
            float outerRadius = f(outer, "Outer radius");
            if (innerRadius > outerRadius) {
                throw new IllegalArgumentException("Inner radius must be less than or equal to the outer radius.");
            }
            return item.withDiskSettings(
                textOr(label, item.label), n(count, "Count"),
                f(cx, "Center X"), f(cy, "Center Y"), f(cz, "Center Z"),
                innerRadius, outerRadius,
                f(massMin, "Particle mass min"), f(massMax, "Particle mass max"),
                f(densityMin, "Density min"), f(densityMax, "Density max"),
                f(centerMass, "Center mass"), f(centerDensity, "Center density"),
                f(nx, "Normal X"), f(ny, "Normal Y"), f(nz, "Normal Z"),
                f(vx, "Added velocity X"), f(vy, "Added velocity Y"), f(vz, "Added velocity Z"),
                f(adherence, "Plane adherence"), f(orbitalFactor, "Orbital factor"),
                ccw.isSelected(), orbital.isSelected());
        });
    }

    private static SceneItem editBox(Window owner, SceneItem item) {
        TextField label = field(item.label);
        TextField count = field(Integer.toString(item.bodyCount));
        TextField cx = field(num(item.cx));
        TextField cy = field(num(item.cy));
        TextField cz = field(num(item.cz));
        TextField extent = field(num(item.halfExtent));
        TextField massMin = field(num(item.massMin));
        TextField massMax = field(num(item.massMax));
        TextField densityMin = field(num(item.densityMin));
        TextField densityMax = field(num(item.densityMax));
        TextField vxMin = field(num(item.vxMin));
        TextField vxMax = field(num(item.vxMax));
        TextField vyMin = field(num(item.vyMin));
        TextField vyMax = field(num(item.vyMax));
        TextField vzMin = field(num(item.vzMin));
        TextField vzMax = field(num(item.vzMax));
        GridPane grid = form(
            row("Label", label),
            row("Count", count),
            row("Center X", cx), row("Center Y", cy), row("Center Z", cz),
            row("Half extent", extent),
            row("Mass min", massMin), row("Mass max", massMax),
            row("Density min", densityMin), row("Density max", densityMax),
            row("Velocity X min", vxMin), row("Velocity X max", vxMax),
            row("Velocity Y min", vyMin), row("Velocity Y max", vyMax),
            row("Velocity Z min", vzMin), row("Velocity Z max", vzMax)
        );
        return show(owner, item.label, grid, () -> item.withBoxSettings(
            textOr(label, item.label), n(count, "Count"),
            f(cx, "Center X"), f(cy, "Center Y"), f(cz, "Center Z"), f(extent, "Half extent"),
            f(massMin, "Mass min"), f(massMax, "Mass max"),
            f(densityMin, "Density min"), f(densityMax, "Density max"),
            f(vxMin, "Velocity X min"), f(vxMax, "Velocity X max"),
            f(vyMin, "Velocity Y min"), f(vyMax, "Velocity Y max"),
            f(vzMin, "Velocity Z min"), f(vzMax, "Velocity Z max")));
    }

    private static SceneItem editSingle(Window owner, SceneItem item) {
        TextField label = field(item.label);
        TextField cx = field(num(item.cx));
        TextField cy = field(num(item.cy));
        TextField cz = field(num(item.cz));
        TextField vx = field(num(item.vx));
        TextField vy = field(num(item.vy));
        TextField vz = field(num(item.vz));
        TextField mass = field(num(item.mass));
        TextField density = field(num(item.density));
        GridPane grid = form(
            row("Label", label),
            row("X", cx), row("Y", cy), row("Z", cz),
            row("Velocity X", vx), row("Velocity Y", vy), row("Velocity Z", vz),
            row("Mass", mass), row("Density", density)
        );
        return show(owner, item.label, grid, () -> item.withSingleSettings(
            textOr(label, item.label),
            f(cx, "X"), f(cy, "Y"), f(cz, "Z"),
            f(vx, "Velocity X"), f(vy, "Velocity Y"), f(vz, "Velocity Z"),
            f(mass, "Mass"), f(density, "Density")));
    }

    private static SceneItem editSpice(Window owner, SceneItem item, UnitSet units) {
        List<PlanetRow> rows = new ArrayList<>();
        VBox body = new VBox(8);
        body.getChildren().add(new Label(item.planets.size() + " bodies from " + item.label
            + ". Hint radius " + num(item.radius) + ", hint mass " + num(item.mass) + "."));
        for (Planet planet : item.planets) {
            PlanetRow row = new PlanetRow(planet);
            rows.add(row);
            body.getChildren().add(row.box);
        }
        return show(owner, item.label, body, () -> {
            List<Planet> planets = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                planets.add(rows.get(i).toPlanet(i + 1));
            }
            return item.withPlanets(planets, units);
        });
    }

    private static void info(Window owner, String title, String message) {
        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(title);
        dialog.getDialogPane().setContent(new Label(message));
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Close", ButtonData.CANCEL_CLOSE));
        applyTheme(dialog.getDialogPane());
        dialog.showAndWait();
    }

    private static SceneItem show(Window owner, String title, javafx.scene.Node content, ItemBuilder builder) {
        Dialog<SceneItem> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(title);
        dialog.setHeaderText("Settings used for this object");
        applyTheme(dialog.getDialogPane());
        Label error = new Label();
        error.setStyle("-fx-text-fill: #a4262c;");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(420);
        scroll.setPrefViewportWidth(460);
        VBox box = new VBox(8, scroll, error);
        box.setPadding(new Insets(4));
        dialog.getDialogPane().setContent(box);
        ButtonType apply = new ButtonType("Apply", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(apply, ButtonType.CANCEL);
        final SceneItem[] edited = new SceneItem[1];
        Button applyButton = (Button) dialog.getDialogPane().lookupButton(apply);
        applyButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                edited[0] = builder.build();
                error.setText("");
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                edited[0] = null;
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == apply ? edited[0] : null);
        return dialog.showAndWait().orElse(null);
    }

    private static void applyTheme(javafx.scene.control.DialogPane pane) {
        var css = SceneItemEditor.class.getResource("/ui/setup.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
    }

    private static GridPane form(LabeledField... rows) {
        GridPane grid = new GridPane();
        fill(grid, rows);
        return grid;
    }

    private static int fill(GridPane grid, LabeledField... rows) {
        grid.setHgap(8);
        grid.setVgap(6);
        for (int i = 0; i < rows.length; i++) {
            grid.add(new Label(rows[i].name), 0, i);
            grid.add(rows[i].field, 1, i);
        }
        return rows.length;
    }

    private static LabeledField row(String name, TextField field) {
        return new LabeledField(name, field);
    }

    private static TextField field(String text) {
        TextField field = new TextField(text);
        field.setPrefColumnCount(12);
        return field;
    }

    private static String textOr(TextField field, String fallback) {
        String text = field.getText() == null ? "" : field.getText().trim();
        return text.isEmpty() ? fallback : text;
    }

    private static float f(TextField field, String name) {
        try {
            float value = Float.parseFloat(field.getText().trim());
            if (!Float.isFinite(value)) {
                throw new NumberFormatException(name);
            }
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException(name + " must be a number.");
        }
    }

    private static int n(TextField field, String name) {
        try {
            int value = Integer.parseInt(field.getText().trim().replace("_", ""));
            if (value < 0) {
                throw new IllegalArgumentException(name + " must be zero or greater.");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer.");
        }
    }

    private static String num(float value) {
        return Float.toString(value);
    }

    @FunctionalInterface
    private interface ItemBuilder {
        SceneItem build();
    }

    private static final class LabeledField {
        final String name;
        final TextField field;

        private LabeledField(String name, TextField field) {
            this.name = name;
            this.field = field;
        }
    }

    private static final class PlanetRow {
        final TextField name;
        final TextField x;
        final TextField y;
        final TextField z;
        final TextField vx;
        final TextField vy;
        final TextField vz;
        final TextField mass;
        final TextField density;
        final UnitSet units;
        final VBox box;

        private PlanetRow(Planet planet) {
            units = planet.getUnitSet();
            name = field(Integer.toString(planet.name));
            x = field(num(planet.position.x));
            y = field(num(planet.position.y));
            z = field(num(planet.position.z));
            vx = field(num(planet.velocity.x));
            vy = field(num(planet.velocity.y));
            vz = field(num(planet.velocity.z));
            mass = field(num(planet.mass));
            density = field(num(planet.density));
            box = new VBox(4, new Label("Body " + planet.name), form(
                row("Name", name),
                row("X", x), row("Y", y), row("Z", z),
                row("Velocity X", vx), row("Velocity Y", vy), row("Velocity Z", vz),
                row("Mass", mass), row("Density", density)
            ));
        }

        private Planet toPlanet(int index) {
            int id;
            try {
                id = Integer.parseInt(name.getText().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Body " + index + " name must be an integer.");
            }
            Planet planet = new Planet(
                f(x, "Body " + index + " X"),
                f(y, "Body " + index + " Y"),
                f(z, "Body " + index + " Z"),
                f(vx, "Body " + index + " velocity X"),
                f(vy, "Body " + index + " velocity Y"),
                f(vz, "Body " + index + " velocity Z"),
                f(mass, "Body " + index + " mass"),
                f(density, "Body " + index + " density"),
                units
            );
            planet.name = id;
            return planet;
        }
    }
}
