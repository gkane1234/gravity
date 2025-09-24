package com.grumbo.simulation;

public class Units {
    public static final double ASTRONOMICAL_UNIT = 1.496e11; //m
    public static final double SOLAR_MASS = 1.989e30; //kg
    public static final double STELLAR_DENSITY = 1.408e3; //kg/m^3
    public static final double GRAVITATIONAL_CONSTANT = 6.67430e-11; //m^3 kg^-1 s^-2
    public static final double PI = 3.14159265358979323846;
    public static final double THREE_OVER_FOUR_PI_TO_THE_THIRD = 0.6203504909; 


    enum Unit {
        ASTRONOMICAL_UNIT,
        PARSEC,
        LIGHT_YEAR,
        METER}


    private Units() {

    }
}
