package com.grumbo;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

public class WindowGravity extends JFrame implements KeyListener, MouseListener, MouseMotionListener, MouseWheelListener {
    
    private GravitySimulator simulator;
    private GravityUI ui;
    private int lastMouseX, lastMouseY;
    private boolean isDragging = false;
    private static final double ZOOM_FACTOR = 1.1;
    public int[] mouseLocation = new int[2];
    private GravityFrame frame;
    
    public WindowGravity(GravitySimulator simulator, GravityFrame frame) throws Exception {
        super("Gravity Simulator Menu V. 1.0.0");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(Global.width, Global.height);
        
        this.simulator = simulator;
        this.ui = new GravityUI(simulator);
        this.frame = frame;
        add(frame);
        // Add window listener to properly shut down threads when window closes
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("Initiating graceful shutdown...");
                simulator.stop();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                simulator.shutdown();
                System.out.println("Shutdown complete.");
                System.exit(0);
            }
        });
        
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        ui.keyPressed(code);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        lastMouseX = e.getX();
        lastMouseY = e.getY();
        isDragging = true;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        isDragging = false;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (isDragging) {
            int dx = e.getX() - lastMouseX;
            int dy = e.getY() - lastMouseY;
            
            simulator.moveCamera(new double[] {dx, dy});
            
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        // Get mouse position
        int mouseX = e.getX();
        int mouseY = e.getY();
        
        // Get simulation coordinates of mouse before zoom
        int[] simCoordsBefore = simulator.getRenderFrame().getSimulationLocation(mouseX, mouseY);
        
        if (e.getWheelRotation() < 0) {
            // Zoom in
            Global.zoom *= ZOOM_FACTOR;
        } else {
            // Zoom out
            Global.zoom /= ZOOM_FACTOR;
        }
        
        // Get new screen coordinates of the same simulation point
        int[] screenCoordsAfter = simulator.getRenderFrame().getScreenLocation(
            simCoordsBefore[0],
            simCoordsBefore[1]
        );
        
        // Move the camera to keep the point under the mouse
        Global.shift[0] += mouseX - screenCoordsAfter[0];
        Global.shift[1] += mouseY - screenCoordsAfter[1];
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseLocation = new int[] {e.getX(), e.getY()};
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // Not needed
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // Not needed
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Not needed
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not needed
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // Not needed
    }
}
