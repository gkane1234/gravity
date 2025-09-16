package com.grumbo.debug;

import java.util.List;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Arrays;

public class Debug {

    public static List<Debug> debugs = new ArrayList<>();

    private static long code  = System.currentTimeMillis();

    private static String fileName = "debug_" + code + ".txt";

    private static String debugFileString = "";

    private static String[] debugsSelected = new String[0];


    private String debugString = "";


    private String name;

    public Debug(String name) {
        this.name = name;
        this.debugString = "";
        debugs.add(this);
    }

    public Debug() {
        this("Debug");
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDebug() {
        return name + ":\n " + debugString;
    }

    public void addToDebugString(String debug) {
        debugString += debug + "\n";
    }

    public void clearDebugString() {
        debugString = "";
    }

    public void setDebugString(String debug) {
        debugString = debug;
    }

    public static void setDebugsSelected(String[] debugsSelected) {
        Debug.debugsSelected = debugsSelected;
    }

    public static void addDebugToFile(int frame) {
        debugFileString += "--------------------------------Frame " + frame + "--------------------------------\n";
        for (Debug debug : debugs) {
            if (Arrays.asList(debugsSelected).contains("ALL")||Arrays.asList(debugsSelected).contains(debug.name)||Arrays.asList(debugsSelected).contains(debug.name.split(" ")[1])) {
                debugFileString += debug.getDebug() + "\n";
            }
        }
        saveDebug();
    }

    public static void saveDebug() {
        try {
            Files.write(Path.of("C:/Users/gkane/Documents/Stuff/gravitychunk/src/main/java/com/grumbo/debug/debug_output/" + fileName), debugFileString.getBytes());
        } catch (IOException e) {
            System.err.println("Failed to save debug: " + e.getMessage());
        }
    }

    public static void outputAllConnectedDebugs() {
        for (Debug debug : debugs) {
            System.out.println(debug.name);
        }
    }
}
