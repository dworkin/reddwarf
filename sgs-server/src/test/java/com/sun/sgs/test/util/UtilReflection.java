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

package com.sun.sgs.test.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;


/** Utilities for using reflection in tests. */
public class UtilReflection {

    /** Returns the specified class. */
    public static Class<?> getClass(String name) {
	try {
	    return Class.forName(name);
	} catch (Exception e) {
	    throw new RuntimeException("Unexpected exception: " + e, e);
	}
    }

    /** Returns the specified declared constructor, making it accessible. */
    public static <T> Constructor<T> getConstructor(
	Class<T> cl, Class<?>... params)
    {
	try {
	    Constructor<T> result = cl.getDeclaredConstructor(params);
	    result.setAccessible(true);
	    return result;
	} catch (Exception e) {
	    throw new RuntimeException("Unexpected exception: " + e, e);
	}
    }

    /** Returns the specified declared field, making it accessible. */
    public static Field getField(Class<?> cl, String fieldName) {
	try {
	    Field result = cl.getDeclaredField(fieldName);
	    result.setAccessible(true);
	    return result;
	} catch (Exception e) {
	    throw new RuntimeException("Unexpected exception: " + e, e);
	}
    }

    /** Returns the specified declared method, making it accessible. */
    public static Method getMethod(
	Class<?> cl, String methodName, Class<?>... params)
    {
	try {
	    Method result = cl.getDeclaredMethod(methodName, params);
	    result.setAccessible(true);
	    return result;
	} catch (Exception e) {
	    throw new RuntimeException("Unexpected exception: " + e, e);
	}
    }
}
