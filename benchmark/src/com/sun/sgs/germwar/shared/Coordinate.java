/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared;

import java.io.Serializable;

/**
 * Represents an abstract 2-dimensional coordinate.
 */
public class Coordinate implements Serializable {
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    private int xPos, yPos;
    
    /*
     * Creates a new {@code Coordinate}.
     */
    public Coordinate(int xPos, int yPos) {
        this.xPos = xPos;
        this.yPos = yPos;
    }

    /**
     * Returns a copy of this {@code Coordinate}. (like a clone, but typed)
     */
    public Coordinate copy() {
        return new Coordinate(xPos, yPos);
    }

    /**
     * Returns a {@link Vector} that "points" from this {@code Coordinate} to
     * {@code target}.
     */
    public Vector diff(Coordinate target) {
        return new Vector(target.getX() - xPos, target.getY() - yPos);
    }

    public boolean equals(Coordinate loc) {
        return (xPos == loc.getX()) &&
            (yPos == loc.getY());
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof Coordinate)
            return equals((Coordinate)o);
        
        return false;
    }
    
    /** Returns value of the X-dimension of this coordinate. */
    public int getX() { return xPos; }
    
    /** Returns value of the Y-dimension of this coordinate. */
    public int getY() { return yPos; }
    
    /**
     * {@inheritDoc}
     *<p>
     * XOR of x and y positions.
     */
    public int hashCode() {
        return (int)(xPos ^ yPos);
    }

    /** Returns a new {@code Coordinate} that is a specified distance away. */
    public Coordinate offsetBy(int x, int y) {
        return new Coordinate(xPos + x, yPos + y);
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(xPos).append(", ").append(yPos).append("]");
        return sb.toString();
    }
}
