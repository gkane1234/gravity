package com.grumbo.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.grumbo.simulation.Property;
import com.grumbo.simulation.Settings;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Encapsulates the settings panel UI: building controls, drawing, and input handling.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class SettingsPane {

    private final Set<UIRow> renderedRows= new HashSet<>();
    private ArrayList<Property<?>> propertyRows = new ArrayList<>();
    private ArrayList<UIRow> titleAndPageButtons = new ArrayList<>();

    private UISlider activeSlider = null;
    private double mouseX = 0.0;
    private double mouseY = 0.0;

    private int currentPage = 0;
    private double pageHeight = 0.0;
    private int maxPage = 1; // make this dynamic

    public boolean textFieldFocused = false;

    /**
     * Constructor for the SettingsPane class.
     * Initializes the title and page buttons and rows for each property from the settings class.
     */
    public SettingsPane() {
        ArrayList<UIElement> titleRow = new ArrayList<>();
        titleRow.add(new UIText("=== SETTINGS " + (currentPage + 1)+  " ==="));
        titleAndPageButtons.add(new UIRow(titleRow));
        ArrayList<UIElement> pageButtonsRow = new ArrayList<>();
        pageButtonsRow.add(new UIButton("Next", () -> {
            currentPage++;
            if (currentPage > maxPage) currentPage = maxPage;
        }));
        pageButtonsRow.add(new UIButton("Prev", () -> {
            currentPage--;
            if (currentPage < 0) currentPage = 0;
        }));
        titleAndPageButtons.add(new UIRow(pageButtonsRow));

        // Property controls (create once; layout every frame)
        for (String name : Settings.getInstance().getPropertyNames()) {
            Property<?> prop = Settings.getInstance().getProperty(name);
            if (prop == null || !prop.isEditable()) continue;
            UIRow row = prop.getEditorRow();
            if (row != null) propertyRows.add(prop);
        }
    }

    // -------- Public input hooks (to be called from window callbacks) --------
    /**
     * Handles the mouse move event.
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    public void onMouseMove(double x, double y) {
        mouseX = x;
        mouseY = y;
        if (activeSlider != null) {
            activeSlider.handleMouseDrag(mouseX, mouseY);
        }
    }

    /**
     * Handles the mouse button event.
     * @param button The button that was pressed.
     * @param action The action that was performed.
     */
    public void onMouseButton(int button, int action) {
        if (button != GLFW_MOUSE_BUTTON_LEFT) return;

        if (action == GLFW_PRESS) {

            activeSlider = null;
            textFieldFocused = false;
            for (UIRow row : renderedRows) {
                for (UIElement element : row.getElements()) {
                    if (element.handleMousePress(mouseX, mouseY)) {
                        if (element instanceof UISlider) {
                            activeSlider = (UISlider) element;
                        }
                        else if (element instanceof UITextField) {
                            if (((UITextField) element).isFocused()) {
                                textFieldFocused = true;
                            }
                        }
                    }
                }
            }
        } else if (action == GLFW_RELEASE) {
            if (activeSlider != null) {
                activeSlider.handleMouseRelease();
                activeSlider = null;
            }
        }
    }

    /**
     * Handles the key event.
     * @param key The key that was pressed.
     * @param action The action that was performed.
     * @param mods The modifiers that were pressed.
     */
    public boolean onKey(int key, int action, int mods) {
        boolean keyPressed = false;
        for (UIRow row : renderedRows) {
                for (UIElement element : row.getElements()) {
                    if (element.handleKeyPress(key, action, mods)) {
                    keyPressed = true;
                    if (element instanceof UITextField) {
                        if (!((UITextField) element).isFocused()) {
                            textFieldFocused = false;
                        }
                    }
                }
            }
        }
        return keyPressed;
    }

    /**
     * Handles the character event.
     * @param codepoint The codepoint of the character that was pressed.
     */
    public boolean onChar(int codepoint) {
        boolean charPressed = false;
        for (UIRow row : renderedRows) {
                for (UIElement element : row.getElements()) {
                    if (element.handleCharPress(codepoint)) {
                    charPressed = true;
                }
            }
        }
        return charPressed;
    }

    // -------- Rendering --------
    /**
     * Draws the settings pane.
     * @param font The font to use.
     */
    public void draw(BitmapFont font) {
        beginUiFrame();

        // Switch to 2D rendering mode
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, Settings.getInstance().getWidth(), Settings.getInstance().getHeight(), 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        // Disable depth testing for UI
        glDisable(GL_DEPTH_TEST);

        glColor3f(1.0f, 1.0f, 1.0f);

        float yPos = 100.0f;
        float xOffset = 20.0f;
        float bottomSpace = 80.0f;
        float bottomY = Settings.getInstance().getHeight() - bottomSpace;
        float verticalPadding = 10.0f;

        // Title (update existing element text and draw)
        ((UIText)titleAndPageButtons.get(0).getElements().get(0)).setText("=== SETTINGS " + (currentPage + 1)+  " ===");
        titleAndPageButtons.get(0).calculateSize(font);
        titleAndPageButtons.get(0).setPosition(xOffset, yPos);
        titleAndPageButtons.get(0).draw(font);
        renderedRows.add(titleAndPageButtons.get(0));
       
        float pageYstart = yPos+titleAndPageButtons.get(0).height+verticalPadding;

        yPos = pageYstart;
        pageHeight = bottomY - pageYstart;

        float pageOffset = (float)pageHeight*currentPage;

        for (Property<?> prop : propertyRows) {
            prop.update();
            UIRow row = prop.getEditorRow();
            row.calculateSize(font);
            float yPosOnPage = (float) (yPos - pageOffset);
            //should have been rendered on a previous page
            //AND was not rendered on last page becuase it was too wide
            if (yPosOnPage<pageYstart && yPosOnPage>pageYstart-row.height) {
                    //snap to this page start
                    yPosOnPage = pageYstart;
                    yPos = yPosOnPage+pageOffset;
                }

            if (yPosOnPage>=pageYstart && yPosOnPage+row.height<=pageYstart+pageHeight) {
                row.setPosition(xOffset, yPosOnPage);
                row.draw(font);
                renderedRows.add(row);
            }
            yPos += row.height+verticalPadding;
        }

        titleAndPageButtons.get(1).calculateSize(font);
        titleAndPageButtons.get(1).setPosition(xOffset, bottomY);
        titleAndPageButtons.get(1).draw(font);
        renderedRows.add(titleAndPageButtons.get(1));

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);

        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    // -------- Render tracking helpers --------
    private void beginUiFrame() {
        renderedRows.clear();
    }

    /**
     * Checks if a row was rendered.
     * @param row The row to check.
     * @return True if the row was rendered, false otherwise.
     */
    public boolean wasRendered(UIRow row) {
        return renderedRows.contains(row);
    }
}
