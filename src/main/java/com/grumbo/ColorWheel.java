package com.grumbo;
import java.awt.*;

public class ColorWheel {

    public static Color hsvToRgb(int h, double s, double v) {
        double hp = h/60.0;
        double c = s * v;
        double x = c * (1 - Math.abs(hp % 2.0 - 1));
        double m = v - c;
        double r = 0, g = 0, b = 0;
        if (hp <= 1) {
            r = c;
            g = x;
        } else if (hp <= 2) {
            r = x;
            g = c;
        } else if (hp <= 3) {
            g = c;
            b = x;
        } else if (hp <= 4) {
            g = x;
            b = c;
        } else if (hp <= 5) {
            r = x;
            b = c;
        } else {
            r = c;
            b = x;
        }
        r += m;
        g += m;
        b += m;
        return new Color((int)(r * 255), (int)(g * 255), (int)(b * 255));
    }
}
