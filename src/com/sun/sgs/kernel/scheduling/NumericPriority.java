package com.sun.sgs.kernel.scheduling;

/**
 * A {@link Priority} implementation based on numeric values where a
 * higher numeric value represents a higher priority.
 *
 * Calling {@link Priority#getWeightedComparison(Priority)} with a
 * <code>NumericPriority</code> and a <code>Priority</code> of a
 * different type will throw an <code>Error</code>.  However, calling
 * {@link NumericPriority#compareTo(Object)} with an object not of
 * type <code>NumbericPriority</code> will not throw an error.
 *
 * @since  1.0
 * @author David Jurgens
 */
public class NumericPriority implements Priority {

    // DEVELOPER NOTE: these default priority implementations could
    // also be remapped to a current standard set of priorities, such
    // as UNIX nice priorities.

    // REMINDER: comment these
    public static final NumericPriority REAL_TIME = new NumericPriority(1024);
    public static final NumericPriority HIGHEST   = new NumericPriority(512);
    public static final NumericPriority HIGH      = new NumericPriority(256);
    public static final NumericPriority NORMAL    = new NumericPriority(128);
    public static final NumericPriority LOW       = new NumericPriority(64);
    public static final NumericPriority LOWEST    = new NumericPriority(32);
    public static final NumericPriority OPTIONAL  = new NumericPriority(-1);
        
    /**
     * The numeric value of the priority of this object.
     */
    private final double priority;

    /**
     * Constructs a priority with the given value to represent its priority.  
     */
    public NumericPriority(double priority) {   
	this.priority = priority;
    }
    
    // uses Priority interface javadoc comment
    public int compareTo(Priority other) {
	if (other instanceof NumericPriority) {
	    NumericPriority p = (NumericPriority)other;
	    double distance = priority - p.priority;
	    // NOTE: use the floor and ceil functions to avoid
	    // round-to-zero if |distance| < 1, since 0 means they
	    // are equal.
	    return (distance < 0) ? (int)(Math.floor(distance)) : (int)(Math.ceil(distance));
	}
	else { 
	    return -1;
	}
    }


    // uses Priority interface javadoc comment
    public double getWeightedComparison(Priority other) {
	if (other instanceof NumericPriority) {
	    NumericPriority p = (NumericPriority)other;
	    return priority - p.priority;
	}
	else {
	    throw new Error("Uncomparable Priorities: " + this + ", " + other);
	}
    }

    public String toString() {
	return "NumericPriority(" + priority + ")";
    }
    
}
