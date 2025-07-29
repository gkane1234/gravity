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
		
		// Draw chunk boundaries
		drawChunkBoundaries(g2d);
		
		// Draw planets first (so text appears on top)
		drawPlanets(g);
		
		// Draw text on top of objects
		drawOutlinedText(g2d, Long.toString(simulator.wait), 10, 10);

		int[] mouseLocation = simulator.window.mouseLocation;
		int[] simulationLocation  = getSimulationLocation(mouseLocation[0], mouseLocation[1]);
		drawOutlinedText(g2d, Long.toString(mouseLocation[0]) + ", " + Long.toString(mouseLocation[1])+
		" | " + Long.toString(simulationLocation[0]) + ", " + Long.toString(simulationLocation[1])+
		" | " + Double.toString(Global.zoom), 30, 10);
		
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
	 * Draws red lines at chunk boundaries
	 */
	private void drawChunkBoundaries(Graphics2D g) {
		g.setColor(Color.RED);
		
		// Get visible area in simulation coordinates
		int[] topLeft = getSimulationLocation(0, 0);
		int[] bottomRight = getSimulationLocation(getWidth(), getHeight());
		
		// Calculate chunk size from Global class
		double chunkSize = Global.chunkSize;
		
		// Calculate chunk grid lines using the same logic as Planet class
		// First, find the chunk coordinates for the corners
		long[] topLeftChunk = Chunk.getChunkCenter(new double[] {topLeft[0], topLeft[1]});
		long[] bottomRightChunk = Chunk.getChunkCenter(new double[] {bottomRight[0], bottomRight[1]});
		
		long startChunkX = topLeftChunk[0] - 1; // -1 to ensure we draw one chunk before
		long endChunkX = bottomRightChunk[0] + 1; // +1 to ensure we draw one chunk after
		long startChunkY = topLeftChunk[1] - 1;
		long endChunkY = bottomRightChunk[1] + 1;
		
		// Draw vertical lines at chunk boundaries
		for (double chunkX = startChunkX; chunkX <= endChunkX; chunkX++) {
			// Convert chunk coordinate back to simulation coordinate
			double x = (chunkX - 0.5) * chunkSize; // Subtract 0.5 to get the boundary
			int[] screenStart = getScreenLocation(x, startChunkY * chunkSize);
			int[] screenEnd = getScreenLocation(x, endChunkY * chunkSize);
			g.drawLine(screenStart[0], screenStart[1], screenEnd[0], screenEnd[1]);
		}
		
		// Draw horizontal lines at chunk boundaries
		for (double chunkY = startChunkY; chunkY <= endChunkY; chunkY++) {
			// Convert chunk coordinate back to simulation coordinate
			double y = (chunkY - 0.5) * chunkSize; // Subtract 0.5 to get the boundary
			int[] screenStart = getScreenLocation(startChunkX * chunkSize, y);
			int[] screenEnd = getScreenLocation(endChunkX * chunkSize, y);
			g.drawLine(screenStart[0], screenStart[1], screenEnd[0], screenEnd[1]);
		}
	}
	public int[] getScreenLocation(double simX, double simY) {
		int[] followLocation = simulator.getReference(Global.follow);
		int screenWidth = this.getSize().width;
		int screenHeight = this.getSize().height;
		
		// Convert simulation coordinates to screen coordinates
		return new int[] {
			(int)(Global.zoom * (simX - followLocation[0]) + (screenWidth/2 + Global.shift[0])),
			(int)(Global.zoom * (simY - followLocation[1]) + (screenHeight/2 + Global.shift[1]))
		};
	}

	public int[] getSimulationLocation(double screenX, double screenY) {
		int[] followLocation = simulator.getReference(Global.follow);
		int screenWidth = this.getSize().width;
		int screenHeight = this.getSize().height;
		
		// Convert screen coordinates to simulation coordinates
		return new int[] {
			(int)((screenX - (screenWidth/2 + Global.shift[0])) / Global.zoom + followLocation[0]),
			(int)((screenY - (screenHeight/2 + Global.shift[1])) / Global.zoom + followLocation[1])
		};
	}
	
	/**
	 * Renders all planets from all chunks
	 */
	private void drawPlanets(Graphics g) {
		ArrayList<Chunk> chunks = simulator.listOfChunks.getChunks();
		for (int i = 0; i < chunks.size(); i++) {
			Chunk chunk = chunks.get(i);
			ArrayList<Planet> planets = chunk.planets;
			
			for (int planet = 0; planet < planets.size(); planet++) {
				Planet p = planets.get(planet);
				g.setColor(p.getColor());
				double planetDiameter = p.getRadius() * 2;
				
				// Get screen coordinates for planet position
				int[] screenPos = getScreenLocation(
					p.x - planetDiameter/2,
					p.y - planetDiameter/2
				);
				//System.out.println(screenPos[0] + ", " + screenPos[1]);
				
				// Draw the planet
				int screenDiameter = (int)Math.max(2, Global.zoom * planetDiameter);
				g.fillOval(
					screenPos[0],
					screenPos[1],
					screenDiameter,
					screenDiameter
				);
			}
		}	
	}
}

