package com.grumbo;
import java.awt.Color;

public final class Global {

	//Starting window stuff


	
	public static int width=1000;
	public static int height=1000;
	
	public static double zoom=0.3;
	
	public static boolean follow=false;
	public static  double[] shift=new double[] {0,0}; 
	
	
	public static final double chunkSize=100000;

	public static final int numThreads=100;
	
	//Planet constants
	
	public static final double DENSITY=1.0;
	public static final double EXPO=-2;
	
	
	public static final double elasticity = 1.0;
	//0x6dbdef
	public static final Color DEFAULT_PLANET_COLOR= new Color(Integer.decode("0x6dbdef"));
	
	public static final Color DEFAULT_BACKGROUND_COLOR= Color.BLACK;
	
	public static final Color DEFAULT_TEXT_COLOR=Color.WHITE;
}


