/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * --
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
     * Casts an object to the required type with no unchecked warnings. <p>
     *
     * This method is similar to using the
     * {@literal @SuppressWarnings("unchecked")} annotation, but is more
     * flexible and often more succinct.  It is more flexible because it can be
     * used to convert the type of an object passed as a method argument, while
     * the annotation needs to be applied to a declaration.  It is more
     * succinct because it avoids needing to restate the type. <p>
     *
     * For example, compare:
     *
     * <pre>
     * &#64;SuppressWarnings("unchecked")
     * Set&lt;Foo&gt; setOfFoo = (Set&lt;Foo&gt;) object;
     * return foo(setOfFoo);
     * </pre>
     *
     * with:
     *
     * <pre>
     * return foo(uncheckedCast(object));
     * </pre>
     *
     * Note that this method cannot be used when the return type is a type
     * variable.
     *
     * @param <T> the result type
     * @param object the object to cast
     * @return the object cast to type {@code T}
     */
    @SuppressWarnings("unchecked")
    public static <T> T uncheckedCast(Object object) {
	return (T) object;
    }

    /**
     * Checks that a variable is not {@code null}, throwing {@link
     * NullPointerException} if it is.  The exception message includes the name
     * of the variable.
     *
     * @param	variableName the name of the variable being checked
     * @param	value the value of the variable to check
     */
    public static void checkNull(String variableName, Object value) {
	if (value == null) {
	    throw new NullPointerException(
		"The value of " + variableName + " must not be null");
	}
    }
}
