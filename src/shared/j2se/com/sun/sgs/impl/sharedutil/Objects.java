/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.sharedutil;

/**
 * Utility methods for working with {@link Object}s.
 */
public final class Objects {

    /**
     * A thread local that is set to a non-null value while safeToString
     * performing an operation that might recurse.
     */
    private static final ThreadLocal<Boolean> callingSafeToString =
	new ThreadLocal<Boolean>();

    /** This class should not be instantiated. */
    private Objects() { }

    /**
     * Returns a string representing the object, returning a failsafe value if
     * the {@code toString} or {@code hashCode} methods throw exceptions or if
     * the method ends up being called recursively.
     *
     * @param object the object or {@code null}
     * @return a string representing the object
     */
    public static String safeToString(Object object) {
	if (object == null) {
	    return "null";
	}
	if (callingSafeToString.get() == null) {
	    callingSafeToString.set(Boolean.TRUE);
	    try {
		try {
		    String result = object.toString();
		    if (result != null && result.length() > 256) {
			result = result.substring(0, 253) + "...";
		    }
		    return result;
		} catch (RuntimeException e) {
		}
		try {
		    return object.getClass().getName() + '@' +
			Integer.toHexString(object.hashCode());
		} catch (RuntimeException e) {
		}
	    } finally {
		callingSafeToString.remove();
	    }
	}
	return object.getClass().getName() + "[identityHashCode=0x" +
	    Integer.toHexString(System.identityHashCode(object)) + "]";
    }

    /**
     * Returns a string representing the object without calling any methods on
     * the object.  Callers can use this method to avoid the accesses that
     * {@code toString} might perform to any ManagedReferences that the object
     * contains, which could lead to exceptions or scaling problems. <p>
     *
     * Returns {@code "null"} if the argument is {@code null}, otherwise
     * returns the concatenation of the fully qualified name of the object's
     * class, the {@code '#'} character, and the identity hash code of the
     * object as returned by {@link System#identityHashCode
     * System.identityHashCode}, represented in base 16.
     *
     * @param object the object or {@code null}
     * @return a string representing the object
     */
    public static String fastToString(Object object) {
	if (object == null) {
	    return "null";
	} else {
	    return object.getClass().getName() + '#' +
		Integer.toHexString(System.identityHashCode(object));
	}
    }

    /**
     * Casts an object to the required type with no unchecked warnings.  Use
     * this method instead of the @SuppressWarnings("unchecked") annotation if
     * there is no way to perform a safe check, for example because the type is
     * generic.
     *
     * @param <T> the result type
     * @param object the object to cast
     * @return the object cast to type {@code T}
     */
    @SuppressWarnings("unchecked")
    public static <T> T uncheckedCast(Object object) {
	return (T) object;
    }
}
