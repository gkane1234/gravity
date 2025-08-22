package com.grumbo.gpu;

public enum VariableType {
    FLOAT,
    INT,
    UINT,
    BOOL,
    UINT64,
    PADDING;

    
    public static int getSize(VariableType type) {
        switch (type) {
            case FLOAT:
                return 4;
            case INT:
                return 4;
            case UINT:
                return 4;
            case BOOL:
                return 1;
            case UINT64:
                return 8;
            case PADDING:
                return 4;
            default:
                throw new IllegalArgumentException("Invalid type: " + type);
        }
    }   
}