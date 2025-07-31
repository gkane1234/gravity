package com.grumbo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class SettingsGenerator {
    
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: SettingsGenerator <input-json> <output-java>");
            System.exit(1);
        }
        
        String jsonFile = args[0];
        String javaFile = args[1];
        
        try {
            generateSettingsClass(jsonFile, javaFile);
            System.out.println("Settings.java generated successfully!");
        } catch (Exception e) {
            System.err.println("Error generating Settings.java: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public static void generateSettingsClass(String jsonFile, String javaFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File(jsonFile));
        JsonNode properties = root.get("properties");
        
        // Read existing Settings.java if it exists
        String existingContent = "";
        File existingFile = new File(javaFile);
        if (existingFile.exists()) {
            existingContent = new String(Files.readAllBytes(Paths.get(javaFile)));
        }
        
        StringBuilder code = new StringBuilder();
        
        // Generate class header
        code.append(generateClassHeader());
        
        // Generate initializeProperties method
        code.append(generateInitializeProperties(properties));
        
        // Generate convenience methods
        code.append(generateConvenienceMethods(properties, existingContent));
        
        // Generate class footer with preserved methods
        code.append(generateClassFooter(existingContent));
        
        // Write to file
        Files.write(Paths.get(javaFile), code.toString().getBytes());
    }
    
    private static String generateClassHeader() {
        return """            
            package com.grumbo;

            import java.awt.Color;
            import java.io.File;
            import java.io.IOException;
            import java.util.HashMap;
            import java.util.Map;
            import com.fasterxml.jackson.databind.ObjectMapper;
            import java.util.ArrayList;
            

            public class Settings {
                
                private static final String SETTINGS_FILE = "settings.json";
                private static Settings instance;
                
                // Property map to store all settings
                private Map<String, Property<?>> properties = new HashMap<>();
                
                private Settings() {
                    initializeProperties();
                    loadSettings();
                }
                
                public static Settings getInstance() {
                    if (instance == null) {
                        instance = new Settings();
                    }
                    return instance;
                }
                public ArrayList<String> getPropertyNames() {
                    return new ArrayList<>(properties.keySet());
                }
                
            """;
    }
    
    private static String generateInitializeProperties(JsonNode properties) {
        StringBuilder code = new StringBuilder();
        code.append("\t// ===== AUTO-GENERATED: Property Initialization =====\n");
        code.append("\t// This section is automatically generated from defaultProperties.json\n");
        code.append("\t// Any changes made here will be overwritten when regenerating\n");
        code.append("\tprivate void initializeProperties() {\n");
        
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String propertyName = entry.getKey();
            JsonNode property = entry.getValue();
            
            String type = property.get("type").asText();
            String defaultValue = property.get("default").asText();
            
            code.append("\t\t// ").append(property.get("description").asText()).append("\n");
            
            switch (type) {
                case "int":
                    String minIntStr = property.has("min") ? String.valueOf(property.get("min").asInt()) : "Integer.MIN_VALUE";
                    String maxIntStr = property.has("max") ? String.valueOf(property.get("max").asInt()) : "Integer.MAX_VALUE";
                    code.append(String.format("\t\tproperties.put(\"%s\", Property.createIntProperty(\"%s\", %s, %s, %s, %s));\n",
                        propertyName, propertyName, defaultValue, defaultValue, minIntStr, maxIntStr));
                    break;
                    
                case "double":
                    String minDoubleStr = property.has("min") ? String.valueOf(property.get("min").asDouble()) : "-Double.MAX_VALUE";
                    String maxDoubleStr = property.has("max") ? String.valueOf(property.get("max").asDouble()) : "Double.MAX_VALUE";
                    code.append(String.format("\t\tproperties.put(\"%s\", Property.createDoubleProperty(\"%s\", %s, %s, %s, %s));\n",
                        propertyName, propertyName, defaultValue, defaultValue, minDoubleStr, maxDoubleStr));
                    break;
                case "float":
                    String minFloatStr = property.has("min") ? String.valueOf((float)property.get("min").asDouble()) + "f" : "-Float.MAX_VALUE";
                    String maxFloatStr = property.has("max") ? String.valueOf((float)property.get("max").asDouble()) + "f" : "Float.MAX_VALUE";
                    code.append(String.format("\t\tproperties.put(\"%s\", Property.createFloatProperty(\"%s\", %sf, %sf, %s,  %s));\n",
                        propertyName, propertyName, defaultValue, defaultValue, minFloatStr, maxFloatStr));
                    break;
                    
                case "boolean":
                    code.append(String.format("\t\tproperties.put(\"%s\", Property.createBooleanProperty(\"%s\", %s, %s));\n",
                        propertyName, propertyName, defaultValue, defaultValue));
                    break;
                    
                case "color":
                    code.append(String.format("\t\tproperties.put(\"%s\", Property.createColorPropertyFromRGB(\"%s\", %s, %s));\n",
                        propertyName, propertyName, defaultValue, defaultValue));
                    break;
                    
                case "doubleArray":
                    // Convert the default value (a JSON array) to a Java double array initializer
                    StringBuilder arrayBuilder = new StringBuilder();
                    arrayBuilder.append("{");
                    for (int i = 0; i < property.get("default").size(); i++) {
                        if (i > 0) arrayBuilder.append(", ");
                        arrayBuilder.append(property.get("default").get(i).asDouble());
                    }
                    arrayBuilder.append("}");
                    String javaArray = arrayBuilder.toString();
                    code.append(String.format("\t\tproperties.put(\"%s\", new Property<>(\"%s\", new double[]%s, new double[]%s));\n",
                        propertyName, propertyName, javaArray, javaArray));
                    break;
                default:
                    code.append(String.format("\t\tproperties.put(\"%s\", Property.createStringProperty(\"%s\", \"%s\", \"%s\"));\n",
                        propertyName, propertyName, defaultValue, defaultValue));
                    break;
            }
            code.append("\n");
        }
        
        code.append("\t}\n");
        code.append("\t// ===== END AUTO-GENERATED: Property Initialization =====\n\n");
        return code.toString();
    }
    
    private static String generateConvenienceMethods(JsonNode properties, String existingContent) {
        StringBuilder code = new StringBuilder();
        code.append("\t// ===== AUTO-GENERATED: Generic Getter/Setter Methods =====\n");
        code.append("\t// These methods provide type-safe access to properties\n");
        code.append("\t// Any changes made here will be overwritten when regenerating\n");
        code.append("\t@SuppressWarnings(\"unchecked\")\n");
        code.append("\tpublic <T> T getValue(String propertyName) {\n");
        code.append("\t\tProperty<T> prop = (Property<T>) properties.get(propertyName);\n");
        code.append("\t\tif (prop == null) {\n");
        code.append("\t\t\tthrow new IllegalArgumentException(\"Property not found: \" + propertyName);\n");
        code.append("\t\t}\n");
        code.append("\t\treturn prop.getValue();\n");
        code.append("\t}\n\n");
        
        code.append("\t@SuppressWarnings(\"unchecked\")\n");
        code.append("\tpublic <T> void setValue(String propertyName, T value) {\n");
        code.append("\t\tProperty<T> prop = (Property<T>) properties.get(propertyName);\n");
        code.append("\t\tif (prop == null) {\n");
        code.append("\t\t\tthrow new IllegalArgumentException(\"Property not found: \" + propertyName);\n");
        code.append("\t\t}\n");
        code.append("\t\tprop.setValue(value);\n");
        code.append("\t}\n");
        code.append("\t// ===== END AUTO-GENERATED: Generic Getter/Setter Methods =====\n\n");
        
        code.append("\t// ===== AUTO-GENERATED: Property-Specific Getter/Setter Methods =====\n");
        code.append("\t// These methods are automatically generated for each property in defaultProperties.json\n");
        code.append("\t// Any changes made here will be overwritten when regenerating\n");
        
        // Get list of methods that are already in preserved section
        Set<String> preservedMethods = getPreservedMethodNames(existingContent);
        
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String propertyName = entry.getKey();
            JsonNode property = entry.getValue();
            String type = property.get("type").asText();
            
            // Generate getter
            String javaType = getJavaType(type);
            String getterName = getGetterName(propertyName, type);
            code.append(String.format("\tpublic %s %s() { return getValue(\"%s\"); }\n", 
                javaType, getterName, propertyName));
            
            // Generate setter
            String setterName = "set" + capitalize(propertyName);
            code.append(String.format("\tpublic void %s(%s value) { setValue(\"%s\", value); }\n", 
                setterName, javaType, propertyName));
            code.append("\n");

            // Only generate toggle method if it's not already in preserved methods
            if (type.equals("boolean")) {
                String toggleMethodName = "toggle" + capitalize(propertyName);
                if (!preservedMethods.contains(toggleMethodName)) {
                    code.append(String.format("\tpublic void %s() { set%s(!is%s()); }\n", 
                        toggleMethodName, capitalize(propertyName), capitalize(propertyName)));
                }
            }
        }
        
        code.append("\t// ===== END AUTO-GENERATED: Property-Specific Getter/Setter Methods =====\n\n");
        return code.toString();
    }
    
    private static String generateClassFooter(String existingContent) {
        StringBuilder footer = new StringBuilder();
        
        // Add fixed methods that are always included
        footer.append("\t// ===== FIXED: Non-Configurable Values =====\n");
        footer.append("\t// These methods return fixed values that are not configurable\n");
        footer.append("\tpublic int getNumThreads() { return 100; }\n");
        footer.append("\t// ===== END FIXED: Non-Configurable Values =====\n\n");
        
        // Extract and preserve existing convenience methods from the old file
        String preservedMethods = extractPreservedMethods(existingContent);
        if (!preservedMethods.isEmpty()) {
            footer.append("\t// ===== PRESERVED: Custom Convenience Methods =====\n");
            footer.append(preservedMethods);
            footer.append("\t// ===== END PRESERVED: Custom Convenience Methods =====\n\n");
        }
        
        // Add utility methods
        footer.append("\t// ===== AUTO-GENERATED: Utility Methods =====\n");
        footer.append("\t// These utility methods are always included\n");
        footer.append("\t// Any changes made here will be overwritten when regenerating\n");
        footer.append("\tpublic <T> void addProperty(String name, Property<T> property) {\n");
        footer.append("\t\tproperties.put(name, property);\n");
        footer.append("\t}\n\n");
        
        footer.append("\tpublic Property<?> getProperty(String name) {\n");
        footer.append("\t\treturn properties.get(name);\n");
        footer.append("\t}\n");
        footer.append("\t// ===== END AUTO-GENERATED: Utility Methods =====\n\n");
        
        // Add loadSettings and saveSettings methods at the very bottom
        footer.append(generateLoadSaveMethods());
        footer.append("}\n");
        
        return footer.toString();
    }
    
    private static String generateLoadSaveMethods() {
        return """
                \t// ===== AUTO-GENERATED: Load/Save Methods =====
                \t// These methods handle JSON serialization/deserialization
                \t// Any changes made here will be overwritten when regenerating
                \tpublic void loadSettings() {
                \t\tFile file = new File(SETTINGS_FILE);
                \t\tif (file.exists()) {
                \t\t\ttry {
                \t\t\t\tObjectMapper mapper = new ObjectMapper();
                \t\t\t\tMap<String, Object> jsonData = mapper.readValue(file, Map.class);
                \t\t\t\t
                \t\t\t\tfor (Map.Entry<String, Object> entry : jsonData.entrySet()) {
                \t\t\t\t\tString key = entry.getKey();
                \t\t\t\t\tObject value = entry.getValue();
                \t\t\t\t\t
                \t\t\t\t\tif (properties.containsKey(key)) {
                \t\t\t\t\t\tProperty<?> prop = properties.get(key);
                \t\t\t\t\t\t
                \t\t\t\t\t\t// Handle different types appropriately
                \t\t\t\t\t\tif (prop.getValue() instanceof Color) {
                \t\t\t\t\t\t\t// Colors are stored as RGB integers
                \t\t\t\t\t\t\tif (value instanceof Integer) {
                \t\t\t\t\t\t\t\t((Property<Color>)prop).setRGBValue((Integer)value);
                \t\t\t\t\t\t\t}
                \t\t\t\t\t\t} else if (prop.getValue() instanceof Float) {
                \t\t\t\t\t\t\t// JSON deserializes numbers as Double, need to convert to Float
                \t\t\t\t\t\t\tif (value instanceof Number) {
                \t\t\t\t\t\t\t\t((Property<Float>)prop).setValue(((Number)value).floatValue());
                \t\t\t\t\t\t\t}
                \t\t\t\t\t\t} else if (prop.getValue() instanceof double[]) {
                \t\t\t\t\t\t\t// Arrays need special handling
                \t\t\t\t\t\t\tif (value instanceof java.util.List) {
                \t\t\t\t\t\t\t\tjava.util.List<?> list = (java.util.List<?>)value;
                \t\t\t\t\t\t\t\tdouble[] array = new double[list.size()];
                \t\t\t\t\t\t\t\tfor (int i = 0; i < list.size(); i++) {
                \t\t\t\t\t\t\t\t\tarray[i] = ((Number)list.get(i)).doubleValue();
                \t\t\t\t\t\t\t\t}
                \t\t\t\t\t\t\t\t((Property<double[]>)prop).setValue(array);
                \t\t\t\t\t\t\t}
                \t\t\t\t\t\t} else {
                \t\t\t\t\t\t\t// For other types, try to set directly
                \t\t\t\t\t\t\ttry {
                \t\t\t\t\t\t\t\t((Property<Object>)prop).setValue(value);
                \t\t\t\t\t\t\t} catch (Exception e) {
                \t\t\t\t\t\t\t\tSystem.err.println("Failed to load property " + key + ": " + e.getMessage());
                \t\t\t\t\t\t\t}
                \t\t\t\t\t\t}
                \t\t\t\t\t}
                \t\t\t\t}
                \t\t\t\t
                \t\t\t\tSystem.out.println("Settings loaded from " + SETTINGS_FILE);
                \t\t\t} catch (IOException e) {
                \t\t\t\tSystem.err.println("Failed to load settings: " + e.getMessage());
                \t\t\t\tSystem.out.println("Using default settings");
                \t\t\t}
                \t\t} else {
                \t\t\tSystem.out.println("Settings file not found, using default settings");
                \t\t}
                \t}
                \t
                \tpublic void saveSettings() {
                \t\ttry {
                \t\t\tObjectMapper mapper = new ObjectMapper();
                \t\t\tMap<String, Object> jsonData = new HashMap<>();
                \t\t\t
                \t\t\tfor (Map.Entry<String, Property<?>> entry : properties.entrySet()) {
                \t\t\t\tString key = entry.getKey();
                \t\t\t\tProperty<?> prop = entry.getValue();
                \t\t\t\t
                \t\t\t\t// Handle different types appropriately
                \t\t\t\tif (prop.getValue() instanceof Color) {
                \t\t\t\t\tjsonData.put(key, ((Property<Color>)prop).getRGBValue());
                \t\t\t\t} else {
                \t\t\t\t\tjsonData.put(key, prop.getValue());
                \t\t\t\t}
                \t\t\t}
                \t\t\t
                \t\t\tmapper.writerWithDefaultPrettyPrinter().writeValue(new File(SETTINGS_FILE), jsonData);
                \t\t\tSystem.out.println("Settings saved to " + SETTINGS_FILE);
                \t\t} catch (IOException e) {
                \t\t\tSystem.err.println("Failed to save settings: " + e.getMessage());
                \t\t}
                \t}
                \t// ===== END AUTO-GENERATED: Load/Save Methods =====
                \t
                """;
    }
    
    private static String extractPreservedMethods(String existingContent) {
        if (existingContent.isEmpty()) {
            return "";
        }
        
        StringBuilder preserved = new StringBuilder();
        String[] lines = existingContent.split("\n");
        boolean inPreservedSection = false;
        boolean foundPreservedStart = false;
        
        for (String line : lines) {
            // Look for the start of preserved section - be more specific
            if (line.contains("// ===== PRESERVED: Custom Convenience Methods =====")) {
                inPreservedSection = true;
                foundPreservedStart = true;
                continue; // Skip the header line itself
            }
            
            // Look for the end of preserved section - be more specific
            if (line.contains("// ===== END PRESERVED: Custom Convenience Methods =====")) {
                inPreservedSection = false;
                break;
            }
            
            if (inPreservedSection) {
                preserved.append(line).append("\n");
            }
        }
        
        return preserved.toString();
    }
    
    private static String getJavaType(String jsonType) {
        switch (jsonType) {
            case "int": return "int";
            case "double": return "double";
            case "float": return "float";
            case "boolean": return "boolean";
            case "color": return "Color";
            case "doubleArray": return "double[]";
            default: return "String";
        }
    }
    
    private static String getGetterName(String propertyName, String type) {
        if (type.equals("boolean")) {
            return "is" + capitalize(propertyName);
        } else {
            return "get" + capitalize(propertyName);
        }
    }
    
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    private static Set<String> getPreservedMethodNames(String existingContent) {
        Set<String> methodNames = new HashSet<>();
        if (existingContent.isEmpty()) {
            return methodNames;
        }
        
        String[] lines = existingContent.split("\n");
        boolean inPreservedSection = false;
        
        for (String line : lines) {
            // Look for the start of preserved section - be more specific
            if (line.contains("// ===== PRESERVED: Custom Convenience Methods =====")) {
                inPreservedSection = true;
                continue;
            }
            
            // Look for the end of preserved section - be more specific
            if (line.contains("// ===== END PRESERVED: Custom Convenience Methods =====")) {
                inPreservedSection = false;
                break;
            }
            
            if (inPreservedSection) {
                // Extract method names from preserved section
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("public ")) {
                    // Extract method name from "public void methodName(" or "public Type methodName("
                    int startIndex = trimmedLine.indexOf("public ") + 7;
                    int endIndex = trimmedLine.indexOf("(");
                    if (endIndex > startIndex) {
                        String methodSignature = trimmedLine.substring(startIndex, endIndex);
                        // Extract just the method name (after the last space)
                        String[] parts = methodSignature.split("\\s+");
                        if (parts.length > 0) {
                            String methodName = parts[parts.length - 1];
                            methodNames.add(methodName);
                        }
                    }
                }
            }
        }
        
        return methodNames;
    }
} 