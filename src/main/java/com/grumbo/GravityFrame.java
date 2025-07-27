package com.grumbo;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.RenderingHints;
import java.util.ArrayList;
import javax.swing.JComponent;

/**
 * GravityFrame - Pure Rendering Engine & UI Display
 * ================================================
 * Handles all rendering, text display, and visual representation.
 * Receives physics data from GravitySimulator for rendering.
 */
public class GravityFrame extends JComponent {
	
	private static final long serialVersionUID = 1L;
	
	// Reference to the physics simulator (passed in constructor)
	private GravitySimulator simulator;
	
	// Render timing (separate from physics timing)
	public long totalRenderTime = 0;
	private long renderStartTime = 0;
	
	public GravityFrame(GravitySimulator simulator) {
		setBackground(Global.DEFAULT_BACKGROUND_COLOR);
		setForeground(Global.DEFAULT_TEXT_COLOR);
		
		// Store reference to the simulator
		this.simulator = simulator;
		
		repaint();
	}

	// Helper method to draw text with white outline for visibility
	private void drawOutlinedText(Graphics2D g2d, String text, int x, int y) {
		// Save original color
		Color originalColor = g2d.getColor();
		
		// Draw white outline (draw text at offset positions)
		g2d.setColor(Color.WHITE);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					g2d.drawString(text, x + dx, y + dy);
				}
			}
		}
		
		// Draw black text on top
		g2d.setColor(Color.BLACK);
		g2d.drawString(text, x, y);
		
		// Restore original color
		g2d.setColor(originalColor);
	}


	@Override
	public void paintComponent(Graphics g) {
		// Track render time on EDT only if performance stats are enabled
		renderStartTime = simulator.showPerformanceStats ? System.nanoTime() : 0;
		
		g.setColor(getBackground());
		g.fillRect(0, 0, getWidth(), getHeight());
		g.setColor(getForeground());
		
		// Cast to Graphics2D for better text rendering
		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		
		// Draw planets first (so text appears on top)
		drawPlanets(g);
		
		// Draw text on top of objects
		drawOutlinedText(g2d, Long.toString(simulator.wait), 10, 10);
		
		// Display performance info on screen only if toggle is enabled
		if (simulator.showPerformanceStats && simulator.frameCount > 0) {
			drawPerformanceStats(g2d);
		} else if (!simulator.showPerformanceStats) {
			drawOutlinedText(g2d, "Press 'p' to show performance stats | 'i' for console output", 10, 30);
		}
		
		// Complete render timing only if performance stats are enabled
		if (simulator.showPerformanceStats) {
			long renderEndTime = System.nanoTime();
			totalRenderTime = (renderEndTime - renderStartTime);
		}
	}
	
	/**
	 * Draws all performance statistics on screen using simulator data
	 */
	private void drawPerformanceStats(Graphics2D g2d) {
		// Get the formatted performance stats from the simulator
		String performanceData = simulator.getDisplayPerformanceStats();
		String[] lines = performanceData.split("\n");
		
		int y = 30;
		int lineHeight = 20;
		int statsHeight = lineHeight * lines.length + 10; // Dynamic height based on actual lines
		
		// Draw semi-transparent background for better readability
		g2d.setColor(new Color(0, 0, 0, 128)); // Semi-transparent black
		g2d.fillRect(5, y - 15, 450, statsHeight);
		
		// Reset to default color
		g2d.setColor(getForeground());
		
		// Draw each line of the performance stats
		for (String line : lines) {
			drawOutlinedText(g2d, line, 10, y);
			y += lineHeight;
		}
	}
	
	/**
	 * Renders all planets from all chunks
	 */
	private void drawPlanets(Graphics g) {
		int[] xy = simulator.getReference(Global.follow);
		
		for (int chunk = 0; chunk < simulator.listOfChunks.size(); chunk++) {
			ArrayList<Planet> planets = simulator.listOfChunks.get(chunk).planets;
			
			for (int planet = 0; planet < planets.size(); planet++) {
				Planet p = planets.get(planet);
				g.setColor(p.getColor());
				int width = (int)(planets.get(planet).getRadius() * 2);
				
				g.fillOval(
					(int)(Global.zoom * (p.x - width/2 - xy[0]) + (Global.shift[0] + this.getSize().width/2)), 
					(int)(Global.zoom * (p.y - width/2 - xy[1]) + (Global.shift[1] + this.getSize().height/2)), 
					(int)Math.max(2, Global.zoom * width), 
					(int)Math.max(2, Global.zoom * width)
				);
			}
		}	
	}
}

