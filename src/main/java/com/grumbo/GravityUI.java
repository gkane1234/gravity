package com.grumbo;

import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;

public class GravityUI {

    private GravitySimulator simulator;

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


    private static final double ZSTEP = 100;

    public ArrayList<KeyEvent> keyEvents = new ArrayList<>();
    public GravityUI(GravitySimulator simulator) {
        this.simulator = simulator;
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_EQUAL, () -> Settings.getInstance().changeZoom(Settings.getInstance().getZoom() * 1.1)));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_MINUS, () -> Settings.getInstance().changeZoom(Settings.getInstance().getZoom() / 1.1)));
        // WASD movement is now handled directly in OpenGLWindow for relative camera movement
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_W, () -> {})); // Forward - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_A, () -> {})); // Left - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_S, () -> {})); // Backward - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_D, () -> {})); // Right - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_Q, () -> {})); // Up - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_E, () -> {})); // Down - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_P, () -> simulator.showPerformanceStats = !simulator.showPerformanceStats));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_I, () -> System.out.println("\n" + simulator.getDisplayPerformanceStats() + "\n")));
       // keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_LEFT, () -> Settings.getInstance().setTickSize(incrementWait(Settings.getInstance().getTickSize(), false))));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F, () -> Settings.getInstance().toggleFollow()));
       // keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_RIGHT, () -> Settings.getInstance().setTickSize(incrementWait(Settings.getInstance().getTickSize(), true))));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_R, () -> simulator.resetPerformanceCounters()));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_UP, () -> {Settings.getInstance().setChunkSize(Settings.getInstance().getChunkSize() * 1.1); simulator.updateChunkSize(Settings.getInstance().getChunkSize());}));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_DOWN, () -> {Settings.getInstance().setChunkSize(Settings.getInstance().getChunkSize() / 1.1); simulator.updateChunkSize(Settings.getInstance().getChunkSize());}));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_B, () -> simulator.drawChunkBorders = !simulator.drawChunkBorders));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_T, () -> Settings.getInstance().setDrawTail(!Settings.getInstance().isDrawTail())));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_SPACE, () -> Settings.getInstance().toggleFollow()));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_LEFT_BRACKET, () -> Settings.getInstance().setTickSize(Settings.getInstance().getTickSize() * 0.9)));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_RIGHT_BRACKET, () -> Settings.getInstance().setTickSize(Settings.getInstance().getTickSize() * 1.1)));
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
    

    

    /**
     * Adjusts simulation speed (tick delay)
     */
    private long incrementWait(long currentSpeed, boolean inc) {
        double log = Math.log10(currentSpeed);
        
        if (inc) {
            int digits = (int)(Math.floor(log));
            currentSpeed += Math.pow(10, digits);
            if (currentSpeed == 0) currentSpeed += 1;
        }
        else {
            if (currentSpeed == 0) return currentSpeed;
            
            int digits = (int)(Math.floor(log) + (log > Math.floor(log) ? 0 : -1));
            currentSpeed -= Math.pow(10, digits);
        }
        
        return currentSpeed;
    }


    
}
