package com.grumbo.ui;


import com.grumbo.simulation.Settings;
/**
 * UISelector class is a selector UI element.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class UISelector extends UIRow {

    private UIButton[] buttons;
    private String selectedOption;



    /**
     * Constructor for the UISelector class.
     * @param name The name of the selector.
     * @param options The options of the selector.
     * @param values The values to return for each option
     */
    public UISelector(String name, String[] options) {
        super();

        buttons = new UIButton[options.length];
        selectedOption = (String) Settings.getInstance().getValue(name);
        for (int i = 0; i < options.length; i++) {
            final String option = options[i];
            UIButton button = new UIButton(option);
            buttons[i] = button;
        }
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            final String option = options[index];
            buttons[i].setOnClick(() -> {
                Settings.getInstance().setValue(name, option);
                Settings.getInstance().saveSettings();
                for (int j = 0; j < options.length; j++) {
                    buttons[j].setSelected(j == index);
                }
            });
            buttons[i].setOnRelease(() -> {});
            buttons[i].setSelected(selectedOption.equals(option));
            elements.add(buttons[i]);
        }
    }
    
    @Override
    public boolean handleMousePress(double mouseX, double mouseY) {
        for (UIButton button : buttons) {
            if (button.handleMousePress(mouseX, mouseY)) {
                selectedOption = button.getText();
                return true;
            }
        }
        return false;
    }
}
