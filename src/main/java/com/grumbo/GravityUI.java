package com.grumbo;

import java.awt.event.KeyEvent;

public class GravityUI {

    private GravitySimulator simulator;

    private static final double ZSTEP = 100;
    public GravityUI(GravitySimulator simulator) {
        this.simulator = simulator;
    }

    public void keyPressed(int code) {
        System.out.println(code);
        switch (code) {
            case KeyEvent.VK_EQUALS:
                simulator.changeZoom(Global.zoom * 1.1);
                break;
            case KeyEvent.VK_MINUS:
                simulator.changeZoom(Global.zoom / 1.1);
                break;
            case KeyEvent.VK_W:
                simulator.moveCamera(new double[] {0, ZSTEP * Global.zoom});
                break;
            case KeyEvent.VK_A:
                simulator.moveCamera(new double[] {ZSTEP * Global.zoom, 0});
                break;
            case KeyEvent.VK_S:
                simulator.moveCamera(new double[] {0, -ZSTEP * Global.zoom});
                break;
            case KeyEvent.VK_D:
                simulator.moveCamera(new double[] {-ZSTEP * Global.zoom, 0});
                break;
            case KeyEvent.VK_P:
                simulator.showPerformanceStats = !simulator.showPerformanceStats;
                break;
            case KeyEvent.VK_I:
                System.out.println("\n" + simulator.getDisplayPerformanceStats() + "\n");
                break;
            case KeyEvent.VK_LEFT:
                simulator.setTickSpeed(incrementWait(simulator.getTickSpeed(), false));
                System.out.println(simulator.getTickSpeed());
                break;
            case KeyEvent.VK_F:
                simulator.toggleFollow();
                break;
            case KeyEvent.VK_RIGHT:
                simulator.setTickSpeed(incrementWait(simulator.getTickSpeed(), true));
                System.out.println(simulator.getTickSpeed());
                break;
            case KeyEvent.VK_R:
                simulator.resetPerformanceCounters();
                break;
            case KeyEvent.VK_UP:
                Global.chunkSize *= 1.1;
                simulator.updateChunkSize(Global.chunkSize);
                break;
            case KeyEvent.VK_DOWN:
                Global.chunkSize /= 1.1;
                simulator.updateChunkSize(Global.chunkSize);
                break;
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
