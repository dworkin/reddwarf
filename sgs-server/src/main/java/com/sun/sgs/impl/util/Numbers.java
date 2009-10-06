/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
