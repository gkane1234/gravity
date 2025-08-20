package com.grumbo.gpu;

import org.lwjgl.glfw.GLFW;

import com.grumbo.simulation.Settings;

import java.util.ArrayList;

public class GravityUI {


    public class KeyEvent {
        public int key;
        public Runnable action;
        public boolean pressed; 

        public KeyEvent(int key, Runnable action) {
            this.key = key;
            this.action = action;
            this.pressed = false;
        }

        public void press() {
            pressed = true;
        }

        public void release() {
            pressed = false;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof KeyEvent) {
                KeyEvent other = (KeyEvent)o;
                return key == other.key;
            }
            if (o instanceof Integer) {
                return key == (Integer)o;
            }
            return false;
        }
    }



    public ArrayList<KeyEvent> keyEvents = new ArrayList<>();
    public GravityUI() {
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_EQUAL, () -> Settings.getInstance().changeZoom(Settings.getInstance().getZoom() * 1.1)));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_MINUS, () -> Settings.getInstance().changeZoom(Settings.getInstance().getZoom() / 1.1)));
        // WASD movement is now handled directly in OpenGLWindow for relative camera movement
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_W, () -> {})); // Forward - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_A, () -> {})); // Left - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_S, () -> {})); // Backward - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_D, () -> {})); // Right - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_Q, () -> {})); // Up - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_E, () -> {})); // Down - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_P, () -> {}));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_I, () -> {}));
       // keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_LEFT, () -> Settings.getInstance().setTickSize(incrementWait(Settings.getInstance().getTickSize(), false))));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F, () -> Settings.getInstance().toggleFollow()));
       // keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_RIGHT, () -> Settings.getInstance().setTickSize(incrementWait(Settings.getInstance().getTickSize(), true))));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_R, () -> {}));

        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_UP, () -> {Settings.getInstance().setDt((float)(Settings.getInstance().getDt() * 1.1));}));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_DOWN, () -> {Settings.getInstance().setDt((float)(Settings.getInstance().getDt() / 1.1));}));
        //theta
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_LEFT, () -> {Settings.getInstance().setTheta((float)(Settings.getInstance().getTheta() * 0.9));}));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_RIGHT, () -> {Settings.getInstance().setTheta((float)(Settings.getInstance().getTheta() * 1.1));}));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_B, () -> {}));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_T, () -> Settings.getInstance().setDrawTail(!Settings.getInstance().isDrawTail())));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_SPACE, () -> Settings.getInstance().toggleFollow()));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_LEFT_BRACKET, () -> Settings.getInstance().setSoftening((float)(Settings.getInstance().getSoftening() * 0.9))));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_RIGHT_BRACKET, () -> Settings.getInstance().setSoftening((float)(Settings.getInstance().getSoftening() * 1.1))));
    }

    public void updateKeys(int key, int action) {
        for (KeyEvent event : keyEvents) {
            if (event.key == key && action == GLFW.GLFW_PRESS) {
                event.press();
            }
            if (event.key == key && action == GLFW.GLFW_RELEASE) {
                event.release();
            }
        }
    }

    public void runKeyFunctions() {
        for (KeyEvent event : keyEvents) {
            if (event.pressed) {
                event.action.run();
            }
        }
    }
    
    
}
