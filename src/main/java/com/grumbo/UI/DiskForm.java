package com.grumbo.ui;

import com.grumbo.simulation.SimulationSetup.SceneItem;

import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Every generator input for a disk. Used both to add a disk and to edit one.
 */
final class DiskForm {

    final TextField label = field("Disk");
    final TextField count = field("50000");
    final TextField cx = field("0");
    final TextField cy = field("0");
    final TextField cz = field("0");
    final TextField inner = field("1");
    final TextField outer = field("800");
    final TextField massMin = field("0.2");
    final TextField massMax = field("2");
    final TextField densityMin = field("1");
    final TextField densityMax = field("1");
    final TextField centerMass = field("50000");
    final TextField centerDensity = field("1");
    final TextField nx = field("0");
    final TextField ny = field("1");
    final TextField nz = field("0");
    final TextField vx = field("0");
    final TextField vy = field("0");
    final TextField vz = field("0");
    final TextField adherence = field("0.98");
    final TextField orbital = field("1");
    final CheckBox ccw = new CheckBox("Counter-clockwise");
    final CheckBox orbitalVelocity = new CheckBox("Give orbital velocity");
    final Label error = new Label();
    final VBox root;

    DiskForm() {
        orbitalVelocity.setSelected(true);
        error.setWrapText(true);
        error.getStyleClass().add("form-error");
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int row = 0;
        row = add(grid, row, "Label", label, "Count", count);
        row = add(grid, row, "X", cx, "Y", cy, "Z", cz);
        row = add(grid, row, "Inner r", inner, "Outer r", outer);
        row = add(grid, row, "Mass min", massMin, "Mass max", massMax);
        row = add(grid, row, "Dens min", densityMin, "Dens max", densityMax);
        row = add(grid, row, "Ctr mass", centerMass, "Ctr dens", centerDensity);
        row = add(grid, row, "Tilt X", nx, "Tilt Y", ny, "Tilt Z", nz);
        row = add(grid, row, "Vel X", vx, "Vel Y", vy, "Vel Z", vz);
        row = add(grid, row, "Adhere", adherence, "Orbital", orbital);
        HBox checks = new HBox(12, ccw, orbitalVelocity);
        checks.setAlignment(Pos.CENTER_LEFT);
        grid.add(checks, 0, row, 2, 1);
        root = new VBox(6, grid, error);
    }

    void load(SceneItem item) {
        label.setText(item.label);
        count.setText(Integer.toString(item.bodyCount));
        cx.setText(num(item.cx));
        cy.setText(num(item.cy));
        cz.setText(num(item.cz));
        inner.setText(num(item.innerRadius));
        outer.setText(num(item.radius));
        massMin.setText(num(item.massMin));
        massMax.setText(num(item.massMax));
        densityMin.setText(num(item.densityMin));
        densityMax.setText(num(item.densityMax));
        centerMass.setText(num(item.centerMass));
        centerDensity.setText(num(item.centerDensity));
        nx.setText(num(item.nx));
        ny.setText(num(item.ny));
        nz.setText(num(item.nz));
        vx.setText(num(item.vx));
        vy.setText(num(item.vy));
        vz.setText(num(item.vz));
        adherence.setText(num(item.adherence));
        orbital.setText(num(item.orbitalFactor));
        ccw.setSelected(item.ccw);
        orbitalVelocity.setSelected(item.giveOrbitalVelocity);
        error.setText("");
    }

    /** Blank label becomes {@code fallback}. Returns null and shows the message when a field is invalid. */
    SceneItem create(String fallback) {
        try {
            error.setText("");
            return read(fallback, null);
        } catch (IllegalArgumentException e) {
            error.setText(e.getMessage());
            return null;
        }
    }

    SceneItem apply(SceneItem item) {
        try {
            error.setText("");
            return read(item.label, item);
        } catch (IllegalArgumentException e) {
            error.setText(e.getMessage());
            return null;
        }
    }

    private SceneItem read(String fallback, SceneItem existing) {
        String name = label.getText() == null ? "" : label.getText().trim();
        if (name.isEmpty()) {
            name = fallback;
        }
        int n = integer(count, "Count");
        float x = number(cx, "X");
        float y = number(cy, "Y");
        float z = number(cz, "Z");
        float innerRadius = number(inner, "Inner r");
        float outerRadius = number(outer, "Outer r");
        if (innerRadius > outerRadius) {
            throw new IllegalArgumentException("Inner r must be less than or equal to outer r.");
        }
        float minMass = number(massMin, "Mass min");
        float maxMass = number(massMax, "Mass max");
        float minDensity = number(densityMin, "Dens min");
        float maxDensity = number(densityMax, "Dens max");
        float core = number(centerMass, "Ctr mass");
        float coreDensity = number(centerDensity, "Ctr dens");
        float normalX = number(nx, "Tilt X");
        float normalY = number(ny, "Tilt Y");
        float normalZ = number(nz, "Tilt Z");
        float velX = number(vx, "Vel X");
        float velY = number(vy, "Vel Y");
        float velZ = number(vz, "Vel Z");
        float plane = number(adherence, "Adhere");
        float orbit = number(orbital, "Orbital");
        boolean spin = ccw.isSelected();
        boolean giveOrbit = orbitalVelocity.isSelected();
        if (existing == null) {
            return SceneItem.newDisk(name, n, x, y, z, innerRadius, outerRadius,
                minMass, maxMass, minDensity, maxDensity, core, coreDensity,
                normalX, normalY, normalZ, velX, velY, velZ, plane, orbit, spin, giveOrbit);
        }
        return existing.withDiskSettings(name, n, x, y, z, innerRadius, outerRadius,
            minMass, maxMass, minDensity, maxDensity, core, coreDensity,
            normalX, normalY, normalZ, velX, velY, velZ, plane, orbit, spin, giveOrbit);
    }

    private static int add(GridPane grid, int row, String a, TextField fa, String b, TextField fb) {
        grid.add(pair(a, fa), 0, row);
        grid.add(pair(b, fb), 1, row);
        return row + 1;
    }

    private static int add(GridPane grid, int row, String a, TextField fa, String b, TextField fb, String c, TextField fc) {
        grid.add(pair(a, fa), 0, row);
        grid.add(pair(b, fb), 1, row);
        grid.add(pair(c, fc), 2, row);
        return row + 1;
    }

    private static HBox pair(String name, TextField field) {
        Label caption = new Label(name);
        caption.setMinWidth(58);
        HBox box = new HBox(4, caption, field);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static TextField field(String text) {
        TextField field = new TextField(text);
        field.setPrefColumnCount(6);
        return field;
    }

    private static String num(float value) {
        return Float.toString(value);
    }

    private static float number(TextField field, String name) {
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

    private static int integer(TextField field, String name) {
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
}
