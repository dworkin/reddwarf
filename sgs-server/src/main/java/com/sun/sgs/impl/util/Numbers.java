/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.util;

/** Utility methods for working with numbers. */
public final class Numbers {

    /** This class should not be instantiated. */
    private Numbers() { }
    
    /**
     * A utility method that adds two non-negative {@code long}s, returning
     * {@link Long#MAX_VALUE} if the value would overflow.
     *
     * @param	x first value
     * @param	y second value
     * @return	the sum
     * @throws	IllegalArgumentException if either argument is negative
     */
    public static long addCheckOverflow(long x, long y) {
	if (x < 0 || y < 0) {
	    throw new IllegalArgumentException(
		"The arguments must not be negative");
	}
	long result = x + y;
	return (result >= 0) ? result : Long.MAX_VALUE;
    }
}
