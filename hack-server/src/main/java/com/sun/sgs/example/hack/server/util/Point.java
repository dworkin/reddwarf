/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.util;

import java.io.Serializable;

/**
 * A 2D integer point implementation
 */
public class Point implements Serializable {
    
    public final int x;
    
    public final int y;

    public Point(int x, int y) {
	this.x = x;
	this.y = y;
    }

    public boolean equals(Object o) {
	if (o instanceof Point) {
	    Point p = (Point)o;
	    return p.x == x && p.y == y;
	}
	return false;
    }

    public int hashCode() {
	return x ^ y;
    }

    public String toString() {
	return "(" + x + "," + y + ")";
    }

}