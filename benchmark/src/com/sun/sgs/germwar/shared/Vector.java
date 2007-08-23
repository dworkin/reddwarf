/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared;

import java.io.Serializable;

/**
 * Represents an abstract 2-dimensional vector from one point to another.
 */
public class Vector implements Serializable {
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    private int xComp, yComp;
    
    /*
     * Creates a new {@code Vector}.
     */
    public Vector(int xComp, int yComp) {
        this.xComp = xComp;
        this.yComp = yComp;
    }
    
    public boolean equals(Vector loc) {
        return (xComp == loc.getX()) &&
            (yComp == loc.getY());
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof Vector)
            return equals((Vector)o);
        
        return false;
    }

    /**
     * Returns the Euclidean (L2) length of this vector.
     */
    public double euclideanLength() {
        return Math.hypot(xComp, yComp);
    }
    
    /** Returns value of the X-component of this vector. */
    public int getX() { return xComp; }
    
    /** Returns value of the Y-component of this vector. */
    public int getY() { return yComp; }
    
    /**
     * {@inheritDoc}
     *<p>
     * XOR of x and y components.
     */
    public int hashCode() {
        return (int)(xComp ^ yComp);
    }

    /**
     * Returns the Manhattan (L1) length of this vector.
     */
    public int manhattanLength() {
        return Math.abs(xComp) + Math.abs(yComp);
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(xComp).append(", ").append(yComp).append("]");
        return sb.toString();
    }
}
