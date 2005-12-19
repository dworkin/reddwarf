/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

package com.sun.multicast.reliable.applications.slinger;

import java.awt.Color;
import java.util.Hashtable;
import java.lang.IllegalArgumentException;

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
public class ColorUtils {

    /**
     * Do not use, this is an all-static class.
     */
    public ColorUtils() {}

    /**
     * Darkens a given color by the specified percentage.
     * @param r The red component of the color to darken.
     * @param g The green component of the color to darken.
     * @param b The blue component of the color to darken.
     * @param percent percentage to darken.  Needs to be <= 1 && >= 0.
     * @return a new Color with the desired characteristics.
     * @exception IllegalArgumentException
     * if the specified percentage value is unacceptable
     */
    public static Color darken(int r, int g, int b, double percent) 
            throws IllegalArgumentException {

        // GeneralUtils.checkValidPercent(percent);

        return new Color(Math.max((int) (r * (1 - percent)), 0), 
                         Math.max((int) (g * (1 - percent)), 0), 
                         Math.max((int) (b * (1 - percent)), 0));
    }

    /**
     * Darkens a given color by the specified percentage.
     * @param to the Color to darken.
     * @param percent percentage to darken.  Needs to be <= 1 && >= 0.
     * @return a new Color with the desired characteristics.
     * @exception IllegalArgumentException
     * if the specified percentage value is unacceptable
     */
    public static Color darken(Color c, double percent) 
            throws IllegalArgumentException {

        // GeneralUtils.checkValidPercent(percent);

        int r, g, b;

        r = c.getRed();
        g = c.getGreen();
        b = c.getBlue();

        return darken(r, g, b, percent);
    }

    /**
     * Lightens a given color by the specified percentage.
     * @param r The red component of the color to lighten.
     * @param g The green component of the color to lighten.
     * @param b The blue component of the color to lighten.
     * @param percent percentage to lighten.  Needs to be <= 1 && >= 0.
     * @return a new Color with the desired characteristics.
     * @exception IllegalArgumentException
     * if the specified percentage value is unacceptable
     */
    public static Color lighten(int r, int g, int b, double percent) 
            throws IllegalArgumentException {

        // GeneralUtils.checkValidPercent(percent);

        int r2, g2, b2;

        r2 = r + (int) ((255 - r) * percent);
        g2 = g + (int) ((255 - g) * percent);
        b2 = b + (int) ((255 - b) * percent);

        return new Color(r2, g2, b2);
    }

    /**
     * Lightens a given color by the specified percentage.
     * @param to the Color to lighten.
     * @param percent percentage to lighten.  Needs to be <= 1 && >= 0.
     * @return a new Color with the desired characteristics.
     * @exception IllegalArgumentException
     * if the specified percentage value is unacceptable
     */
    public static Color lighten(Color c, double percent) 
            throws IllegalArgumentException {

        // GeneralUtils.checkValidPercent(percent);

        int r, g, b;

        r = c.getRed();
        g = c.getGreen();
        b = c.getBlue();

        return lighten(r, g, b, percent);
    }

    /**
     * Fades from one color to another by the given percentage.
     * @param from the Color to fade from.
     * @param to the Color to fade to.
     * @param percent percentage to fade.  Needs to be <= 1 && >= 0.
     * @return a new Color with the desired characteristics.
     * @exception IllegalArgumentException
     * if the specified percentage value is unacceptable
     */
    public static Color fade(Color from, Color to, 
                             double percent) throws IllegalArgumentException {

        // GeneralUtils.checkValidPercent(percent);

        int from_r, from_g, from_b;
        int to_r, to_g, to_b;
        int r, g, b;

        from_r = from.getRed();
        from_g = from.getGreen();
        from_b = from.getBlue();
        to_r = to.getRed();
        to_g = to.getGreen();
        to_b = to.getBlue();

        if (from_r > to_r) {
            r = to_r + (int) ((from_r - to_r) * (1 - percent));
        } else {
            r = to_r - (int) ((to_r - from_r) * (1 - percent));
        }
        if (from_g > to_r) {
            g = to_g + (int) ((from_g - to_g) * (1 - percent));
        } else {
            g = to_g - (int) ((to_g - from_g) * (1 - percent));
        }
        if (from_b > to_b) {
            b = to_b + (int) ((from_b - to_b) * (1 - percent));
        } else {
            b = to_b - (int) ((to_b - from_b) * (1 - percent));
        }

        return new Color(r, g, b);
    }

    /**
     * Given a Color this function determines the lightness percent.
     * @param c The Color to calculate from.  If null, it will return 0.
     * @return the percent light of the specified color.  This value will be
     * >= 0 && <= 1.
     */
    public static double lightness(Color c) {
        if (c == null) {
            return 0;
        } 

        double r, g, b, max, min;

        r = c.getRed();
        g = c.getGreen();
        b = c.getBlue();
        max = (Math.max(r, Math.max(g, b)) / 255) / 2;
        min = (Math.min(r, Math.min(g, b)) / 255) / 2;

        return (max + min);
    }

    /**
     * Used to calculate a hilight color from a given color.
     * @param c The color to use in the calculation.  If null, then
     * it will return null.
     * @return the newly calculated hilight color.
     */
    public static Color calculateHilightColor(Color c) {
        if (c == null) {
            return null;
        } 

        double lightness = lightness(c);

        if (lightness >= 0.90) {
            return (ColorUtils.darken(c, 0.100));
        }
        if (lightness <= 0.20) {
            return (ColorUtils.lighten(c, 0.600));
        }

        return (ColorUtils.lighten(c, 0.600));
    }

    /**
     * Used to calculate a shadow color from a given color.
     * @param c The color to use in the calculation  If null, then
     * it will return null.
     * @return the newly calculated shadow color.
     */
    public static Color calculateShadowColor(Color c) {
        if (c == null) {
            return null;
        } 

        double lightness = lightness(c);

        if (lightness >= 0.90) {
            return (ColorUtils.darken(c, 0.250));
        }
        if (lightness <= 0.20) {
            return (ColorUtils.lighten(c, 0.200));
        }

        return (ColorUtils.darken(c, 0.250));
    }

}

