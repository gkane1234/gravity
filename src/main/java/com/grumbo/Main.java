package com.grumbo;

/**
 * GRAVITY SIMULATION - CODE HIERARCHY & FLOW
 * ==========================================
 * 
 * 1. Main (Entry Point)
 *    └── Creates windowGravity instance
 *    └── Starts the entire simulation
 * 
 * 2. WindowGravity (JFrame - Main Window & Input Handler)
 *    ├── Extends JFrame - creates the application window
 *    ├── Implements KeyListener - handles user input (WASD camera, zoom, etc.)
 *    ├── Contains GravitySimulator instance - the primary controller
 *    ├── Runs main simulation loop with timing control (tickSpeed)
 *    └── Controls:
 *        • '=' / '-' : Zoom in/out
 *        • 'w','a','s','d' : Camera movement  
 *        • 'p' : Toggle performance stats
 *        • 'f' : Toggle follow mode
 *        • '[' / ']' : Adjust simulation speed
 * 
 * 3. GravitySimulator (Primary Controller - Physics Engine & Simulation Logic)
 *    ├── Main controller that manages both physics and rendering
 *    ├── Contains ArrayList<Chunk> - spatial partitioning system
 *    ├── Contains GravityFrame instance - for rendering output
 *    ├── Main methods:
 *    │   • chunkTick() - main physics simulation step + triggers rendering
 *    │   • calculateAttraction() - computes gravity forces between chunks
 *    │   • moveAndUpdateChunks() - updates positions & chunk assignments
 *    │   • addPlanetToCorrectChunk() - manages planet-to-chunk assignment
 *    │   • changeZoom() / moveCamera() / toggleFollow() - camera controls
 *    ├── Performance profiling system (physics timing)
 *    └── Controls when and how rendering occurs
 * 
 * 4. GravityFrame (JComponent - Pure Rendering Engine & UI Display)
 *    ├── Extends JComponent - handles all rendering and UI display
 *    ├── Receives GravitySimulator reference in constructor
 *    ├── Main methods:
 *    │   • paintComponent() - renders planets and performance UI
 *    │   • drawPlanets() - renders all planets from simulator chunks
 *    │   • drawPerformanceStats() - displays performance metrics
 *    ├── Queries simulator for all data (chunks, planets, timing, etc.)
 *    ├── Outlined text rendering for always-visible UI
 *    └── Render timing tracking (separate from physics timing)
 * 
 * 5. Chunk (Spatial Partitioning Container)
 *    ├── Contains ArrayList<Planet> - planets in this spatial region
 *    ├── Has center coordinates and size (chunkSize from Global)
 *    ├── Methods:
 *    │   • attract() - calculates forces between chunks
 *    │   • move() - updates all planets in this chunk
 *    │   • addPlanet() / removePlanet() - manages planet membership
 *    └── Optimizes O(n²) gravity calculations by grouping nearby planets
 * 
 * 6. Planet (Individual Physics Object)
 *    ├── Position: x, y coordinates
 *    ├── Velocity: xVelocity, yVelocity 
 *    ├── Physics: mass, radius (calculated from mass)
 *    ├── Rendering: color (from ColorWheel)
 *    ├── Methods:
 *    │   • move() - updates position based on velocity
 *    │   • attract() - applies gravitational force between planets
 *    │   • merge() - combines two planets on collision
 *    │   • getColor() - returns color based on mass
 *    └── Factory method: makeNew() - creates arrays of random planets
 * 
 * 7. Global (Configuration & Shared State)
 *    ├── Static constants and variables shared across classes
 *    ├── Zoom level, camera shift, follow mode
 *    ├── Chunk size for spatial partitioning
 *    ├── Window dimensions and colors
 *    └── Simulation parameters
 * 
 * 8. ColorWheel (Visual Enhancement)
 *    ├── Maps planet mass to colors
 *    ├── Creates visual variety in the simulation
 *    └── Helps distinguish different sized objects
 * 
 * SIMULATION FLOW:
 * ================
 * Main Loop (WindowGravity):
 *   1. Call GravitySimulator.chunkTick()
 *      a. Calculate forces between all chunk pairs
 *      b. Move all planets based on accumulated forces  
 *      c. Reassign planets to correct chunks if they moved
 *      d. Remove empty chunks, create new ones as needed
 *      e. Call GravityFrame.repaint() to trigger rendering
 *   2. GravityFrame.paintComponent() (triggered by repaint)
 *      a. Query GravitySimulator for chunk/planet data
 *      b. Draw all planets with correct colors and positions
 *      c. Render performance stats from both physics and render timing
 *      d. Apply camera transformations (zoom, shift)
 *   3. Sleep for tickSpeed milliseconds
 *   4. Repeat
 * 
 * PERFORMANCE OPTIMIZATIONS:
 * =========================
 * • Spatial Chunking: Groups nearby planets to reduce O(n²) calculations
 * • Chunk-to-Chunk Forces: Distant chunks use center-of-mass approximation
 * • Controller Pattern: GravitySimulator controls both physics and rendering timing
 * • Pure Rendering: GravityFrame is stateless and only queries simulator for data
 * • Conditional Profiling: Performance tracking only when enabled
 * • Dual Timing System: Physics timing (main thread) + render timing (EDT) tracked separately
 * • Efficient Rendering: Planets drawn before text overlay with anti-aliased outlined text
 */

public class Main {
	public static void main(String[] args) throws Exception {
		// Initialize and start the gravity simulation
		WindowGravity g = new WindowGravity();
		
	}
}
