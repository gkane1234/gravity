package com.grumbo.UI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.grumbo.simulation.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Encapsulates the settings panel UI: building controls, drawing, and input handling.
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


    public SettingsPane() {
        //Instead of creating them as it is rendered each time, create all of them here explicitly, get rid of the ensure methods. For each property bunch them together for each one somehow
        // Title and page buttons (top title; bottom paging buttons are laid out in draw)
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
    public void onMouseMove(double x, double y) {
        mouseX = x;
        mouseY = y;
        if (activeSlider != null) {
            activeSlider.handleMouseDrag(mouseX, mouseY);
        }
    }


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

    public boolean onKey(int key, int action, int mods) {
        boolean keyPressed = false;
        for (UIRow row : renderedRows) {
                for (UIElement element : row.getElements()) {
                    if (element.handleKeyPress(key, action, mods)) {
                    keyPressed = true;
                }
            }
        }
        return keyPressed;
    }

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

        float yPos = 30.0f;
        float xOffset = 20.0f;
        float bottomSpace = 80.0f;
        float bottomY = Settings.getInstance().getHeight() - bottomSpace;
        float verticalPadding = 20.0f;


        // Title (update existing element text and draw)
        ((UIText)titleAndPageButtons.get(0).getElements().get(0)).setText("=== SETTINGS " + (currentPage + 1)+  " ===");
        titleAndPageButtons.get(0).calculateSize(font);
        titleAndPageButtons.get(0).setPosition(xOffset, yPos);
        titleAndPageButtons.get(0).draw(font);
        renderedRows.add(titleAndPageButtons.get(0));
       
        float pageY = yPos+titleAndPageButtons.get(0).getHeight(font)+verticalPadding;
        pageHeight = bottomY - pageY;

        for (Property<?> prop : propertyRows) {
            prop.update();
            UIRow row = prop.getEditorRow();
            row.calculateSize(font);
            float yPosOnPage = (float) (yPos - pageHeight*currentPage);
            if (yPosOnPage>=pageY && yPosOnPage+row.getHeight(font)<=pageY+pageHeight) {
                row.setPosition(xOffset, yPosOnPage);
                row.draw(font);
                renderedRows.add(row);
            }
            yPos += row.getHeight(font)+verticalPadding;
            
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


    public boolean wasRendered(UIRow row) {
        return renderedRows.contains(row);
    }
}
