package com.grumbo.debug;

import java.util.List;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Arrays;

/**
 * Debug is a class that represents a debug object.
 * It is used to debug the code by logging to a file.
 * The debug object can be selected by setting the debugsSelected array. Available debugs can be found by running outputAllConnectedDebugs().
 * Each ComputeShader object has a debug object before and after the shader is run that can be changed in BoundedBarnesHut.
 * 
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class Debug {

    public static List<Debug> debugs = new ArrayList<>();

    private static long code  = System.currentTimeMillis();

    private static String fileName = "debug_" + code + ".txt";

    private static String debugFileString = "";

    private static String[] debugsSelected = new String[0];


    private String debugString;


    private String name;
    
    //The interval at which the debug is logged to the file, 0 means log once on the first frame, -1 means never log.
    private int debugInterval;
    
    //A count of how many times the debug has been asked for output.
    private int frame;
  

    /**
     * Constructor for the Debug class.
     * @param name the name of the debug
     * @param debugInterval the interval at which the debug is logged to the file, 0 means log once on the first frame, -1 means never log.
     */
    public Debug(String name, int debugInterval) {
        this.name = name;
        this.debugString = "";
        this.debugInterval = debugInterval;
        this.frame = 0;
        debugs.add(this);
    }

    /**
     * Constructor for the Debug class.
     * @param name the name of the debug
     */
    public Debug(String name) {
        this(name, 1);
    }

    /**
     * Default constructor for the Debug class.
     */
    public Debug() {
        this("Debug");
    }

    /**
     * Sets the name of the debug.
     * @param name the name of the debug
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the debug output.
     * If the debugInterval is -1 or the debugString is empty, returns an empty string.
     * If the debugInterval is 0, sets the debugInterval to -1 and returns the debug output.
     * If the frame is divisible by the debugInterval, returns the debug output.
     * Otherwise, returns an empty string.
     * @return the debug output
     */
    public String getDebug() {
        if (debugInterval == -1 || debugString.length() == 0) {
            return "";
        }
        if (debugInterval == 0) {
            debugInterval = -1;
            return name + ":\n " + debugString;
        }

        frame++;
        if (frame % debugInterval == 0) {
            return name + ":\n " + debugString;
        }
        return "";
    }

    /**
     * Adds to the debug output.
     * @param debug the debug to add
     */
    public void addToDebugString(String debug) {
        debugString += debug + "\n";
    }

    /**
     * Clears the debug output.
     */
    public void clearDebugString() {
        debugString = "";
    }

    /**
     * Checks if the debug is selected.
     * @return true if the debug is selected, false otherwise
     */
    public boolean isSelected() {
        boolean containsAll = Arrays.asList(debugsSelected).contains("ALL");
        boolean containsNameWithPreOrPost = Arrays.asList(debugsSelected).contains(name);
        boolean containsName = false;
        //If the name contains a space, the first part is the pre or post and the second part is the name.
        if (name.split(" ").length > 1) {
            containsName = Arrays.asList(debugsSelected).contains(name.split(" ")[1]);
        } else {
            containsName = false;
        }
        return containsAll||containsNameWithPreOrPost||containsName;
    }

    /**
     * Sets the debug output.
     * @param debug the debug to set
     */
    public void setDebugString(String debug) {
        debugString = debug;
    }

    /**
     * Sets the debugs selected.
     * @param debugsSelected the debugs selected
     */
    public static void setDebugsSelected(String[] debugsSelected) {
        Debug.debugsSelected = debugsSelected;
    }

    /**
     * Adds the debug to the file.
     * @param frame the frame
     */
    public static void addDebugToFile(int frame) {
        debugFileString += "--------------------------------Frame " + frame + "--------------------------------\n";
        String debugOutputs = "";
        for (Debug debug : debugs) {
            if (debug.isSelected()) {
                System.out.println(debug.name);
                debugOutputs += debug.getDebug() + "\n";
            }
        }
        if (debugOutputs.length() == 0) {
            return;
        }
        debugFileString += debugOutputs;
        saveDebug();
    }

    /**
     * Saves the debug to the file.
     */
    public static void saveDebug() {
        try {
            Files.write(Path.of("C:/Users/gkane/Documents/Stuff/gravitychunk/src/main/java/com/grumbo/debug/debug_output/" + fileName), debugFileString.getBytes());
        } catch (IOException e) {
            System.err.println("Failed to save debug: " + e.getMessage());
        }
    }

    /**
     * Outputs all connected debugs.
     */
    public static void outputAllConnectedDebugs() {
        for (Debug debug : debugs) {
            System.out.println(debug.name);
        }
    }
}
