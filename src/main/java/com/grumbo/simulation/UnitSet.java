package com.grumbo.simulation;
class Dimensions{
    /**
     * Constructor for the Dimensions class.
     * @param length
     * @param mass
     * @param time
     */

    public static final Dimensions LENGTH = new Dimensions(1, 0, 0);
    public static final Dimensions MASS = new Dimensions(0, 1, 0);
    public static final Dimensions TIME = new Dimensions(0, 0, 1);
    public static final Dimensions DENSITY = new Dimensions(-3, 1, 0);
    public static final Dimensions GRAVITATIONAL_CONSTANT = new Dimensions(3, -1, -2);

    public Dimensions(int length, int mass, int time) {
        this.length = length;
        this.mass = mass;
        this.time = time;
    }

    private int length;
    private int mass;
    private int time;
    
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        Dimensions other = (Dimensions) obj;
        return this.length == other.length && this.mass == other.mass && this.time == other.time;
    }
}




class Unit {

    public enum SIPrefix {
        YOTTA(24),
        ZETTA(21),
        EXA(18),
        PETA(15),
        TERA(12),
        GIGA(9),
        MEGA(6),
        KILO(3),
        HECTO(2),
        DEKA(1),
        NONE(0),
        DECI(-1),
        CENTI(-2),
        MILLI(-3),
        MICRO(-6),
        NANO(-9),
        PICO(-12),
        FEMTO(-15),
        ATTO(-18),
        ZEPTO(-21),
        YOCTO(-24);
    
        private SIPrefix(int exponent) {
            this.exponent = exponent;
        }
    
        private int exponent;
    }

    public static final Unit ASTRONOMICAL_UNIT = new Unit(1.496e11, Dimensions.LENGTH); //m
    public static final Unit STELLAR_RADIUS = new Unit(6.96e8, Dimensions.LENGTH); //m
    public static final Unit SOLAR_MASS = new Unit(1.989e30, Dimensions.MASS); //kg
    public static final Unit STELLAR_DENSITY = new Unit(1.408e3, Dimensions.DENSITY); //kg/m^3
    public static final Unit GRAVITATIONAL_CONSTANT = new Unit(6.67430e-11, Dimensions.GRAVITATIONAL_CONSTANT); //m^3 kg^-1 s^-2
    public static final Unit SECOND = new Unit(1, Dimensions.TIME); //s

    public Dimensions dimensions;
    public double value;
    public SIPrefix siprefix;

    public Unit(double value, Dimensions dimensions, SIPrefix siprefix) {
        this.dimensions = dimensions;
        this.value = value;
        this.siprefix = siprefix;
    }

    public Unit(double value, Dimensions dimensions) {
        this(value, dimensions, SIPrefix.NONE);
    }
    /**
     * Gets the value of the unit in its base units (resolving the SI prefix)
     * @return the value of the unit
     */
    public double getValue() {
        return value * Math.pow(10, siprefix.exponent);
    }

}
public class UnitSet {

    public static final double PI = 3.14159265358979323846;
    public static final double THREE_OVER_FOUR_PI_TO_THE_THIRD = 0.6203504909; 

    public static final UnitSet SOLAR_SYSTEM = new UnitSet(Unit.SOLAR_MASS, Unit.STELLAR_DENSITY, Unit.ASTRONOMICAL_UNIT, Unit.SECOND);
    public static final UnitSet ASTRONOMICAL = new UnitSet(Unit.SOLAR_MASS, Unit.STELLAR_DENSITY, Unit.ASTRONOMICAL_UNIT, Unit.SECOND);
    
    //The unit of a body's mass (in kg) e.g. solar mass
    private Unit mass;
    //The unit of a body's length (in m) e.g. stellar radius
    private Unit density;
    //The unit of space's length (in m) what the simulation space is measured in e.g AU
    private Unit len;
    //The unit of time (in s) e.g. year, day, hour, minute, second
    private Unit time;
    
    public UnitSet(Unit mass, Unit density, Unit len, Unit time) {

        if (mass.dimensions != Dimensions.MASS) {
            throw new IllegalArgumentException("Body mass must be in kg");
        }
        if (density.dimensions != Dimensions.DENSITY) {
            throw new IllegalArgumentException("Body length must be in m");
        }
        if (len.dimensions != Dimensions.LENGTH) {
            throw new IllegalArgumentException("Space length must be in m");
        }
        if (time.dimensions != Dimensions.TIME) {
            throw new IllegalArgumentException("Time must be in s");
        }

        this.mass = mass;
        this.density = density;
        this.len = len;
        this.time = time;
    }

    public double mass() {
        return mass.getValue();
    }
    public double density() {
        return density.getValue();
    }
    public double len() {
        return len.getValue();
    }
    public double time() {
        return time.getValue();
    }





    /**
     * Gets the gravitational constant for a body.
     * Includes 
     * @return the gravitational constant for a body
     */

    public double gravitationalConstant() {
        double G = Unit.GRAVITATIONAL_CONSTANT.getValue()* Math.pow(len.getValue(), 3)* Math.pow(time.getValue(), -2) * Math.pow(mass.getValue(), -1); //m^3 kg^-1 s^-2
        return G;
    }


}
