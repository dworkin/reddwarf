/*
 * Copyright (c) 2007-2008, Sun Microsystems, Inc.
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
 */

package com.sun.sgs.impl.sharedutil;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

/**
 * Wrapper around a {@link Properties} that provides convenience methods for
 * accessing primitives.
 */
public class PropertiesWrapper {

    /** The underlying properties. */
    private final Properties properties;

    /**
     * Creates an instance that delegates to the given <code>Properties</code>.
     *
     * @param	properties the <code>Properties</code> to wrap
     */
    public PropertiesWrapper(Properties properties) {
	if (properties == null) {
	    throw new NullPointerException("The argument must not be null");
	}
	this.properties = properties;
    }
    
    /**
     * Returns the associated <code>Properties</code>.
     *
     * @return	the associated <code>Properties</code>
     */
    public Properties getProperties() {
	return properties;
    }

    /**
     * Returns the value of a property as a <code>String</code>, or
     * <code>null</code> if the property is not found.
     *
     * @param	name the property name
     * @return	the value or <code>null</code>
     */
    public String getProperty(String name) {
	return properties.getProperty(name);
    }

    /**
     * Returns the value of a property as a <code>String</code>, or the default
     * value if the property is not found.
     *
     * @param	name the property name
     * @param	defaultValue the default value
     * @return	the value
     */
    public String getProperty(String name, String defaultValue) {
	return properties.getProperty(name, defaultValue);
    }

    /**
     * Returns the value of a <code>boolean</code> property.
     *
     * @param	name the property name
     * @param	defaultValue the default value
     * @return	the value
     */
    public boolean getBooleanProperty(String name, boolean defaultValue) {
	String value = properties.getProperty(name);
	return value == null ? defaultValue : Boolean.valueOf(value);
    }

    /**
     * Returns the value of an <code>int</code> property.
     *
     * @param	name the property name
     * @param   defaultValue the default value
     * @return	the value
     * @throws	NumberFormatException if the value does not contain a parsable
     *		<code>int</code>
     */
    public int getIntProperty(String name, int defaultValue) {
	String value = properties.getProperty(name);
	if (value == null) {
            return defaultValue;
	}
	try {
	    return Integer.parseInt(value);
	} catch (NumberFormatException e) {
	    throw (NumberFormatException) new NumberFormatException(
		"The value of the " + name + " property must be a valid " +
		"int: \"" + value + "\"").initCause(e);
	}
    }

    /**
     * Returns the value of a required <code>int</code> property.
     *
     * @param   name the property name
     * @return  the value
     * @throws  IllegalArgumentException if the value is not set
     * @throws  NumberFormatException if the value does not contain a parsable
     *          <code>int</code>
     */
    public int getRequiredIntProperty(String name) {
        String value = properties.getProperty(name);
        if (value == null) {
            throw new IllegalArgumentException(
                "The " + name + " property must be specified");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw (NumberFormatException) new NumberFormatException(
                "The value of the " + name + " property must be a valid " +
                "int: \"" + value + "\"").initCause(e);
        }
    }

    /**
     * Returns the value of an {@code int} property within a bound range of
     * values.
     *
     * @param	name the property name
     * @param	defaultValue the default value
     * @param	min the minimum value to allow
     * @param	max the maximum value to allow
     * @return	the value
     * @throws	IllegalArgumentException if the value or {@code defaultValue}
     *		is less than {@code min} or greater than {@code max}, or if
     *		{@code min} is greater than {@code max}
     * @throws	NumberFormatException if the value does not contain a parsable
     *		<code>int</code>
     */
    public int getIntProperty(
	String name, int defaultValue, int min, int max)
    {
	if (min > max) {
	    throw new IllegalArgumentException(
		"The min must not be greater than the max");
	} else if (min > defaultValue || defaultValue > max) {
	    throw new IllegalArgumentException(
		"The default value must be between the min and the max");
	}
	int result = getIntProperty(name, defaultValue);
	if (min > result) {
	    throw new IllegalArgumentException(
		"The value of the " + name + " property must not be less " +
		"than " + min + ": " + result);
	} else if (result > max) {
	    throw new IllegalArgumentException(
		"The value of the " + name + " property must not be greater " +
		"than " + max + ": " + result);
	}
	return result;
    }

    /**
     * Returns the value of a required {@code int} property within a bound
     * range of values.
     *
     * @param   name the property name
     * @param   min the minimum value to allow
     * @param   max the maximum value to allow
     * @return  the value
     * @throws  IllegalArgumentException if the value or {@code defaultValue}
     *          is less than {@code min} or greater than {@code max}, or if
     *          {@code min} is greater than {@code max}, or if the value
     *          is not set
     * @throws  NumberFormatException if the value does not contain a parsable
     *          <code>int</code>
     */
    public int getRequiredIntProperty(String name, int min, int max)
    {
        if (min > max) {
            throw new IllegalArgumentException(
                "The min must not be greater than the max");
        }
        int result = getRequiredIntProperty(name);
        if (min > result) {
            throw new IllegalArgumentException(
                "The value of the " + name + " property must not be less " +
                "than " + min + ": " + result);
        } else if (result > max) {
            throw new IllegalArgumentException(
                "The value of the " + name + " property must not be greater " +
                "than " + max + ": " + result);
        }
        return result;
    }

    /**
     * Returns the value of a <code>long</code> property.
     *
     * @param	name the property name
     * @param	defaultValue the default value
     * @return	the value
     * @throws	NumberFormatException if the value does not contain a parsable
     *		<code>long</code>
     */
    public long getLongProperty(String name, long defaultValue) {
	String value = properties.getProperty(name);
	if (value == null) {
	    return defaultValue;
	}
	try {
	    return Long.parseLong(value);
	} catch (NumberFormatException e) {
	    throw (NumberFormatException) new NumberFormatException(
		"The value of the " + name + " property must be a valid " +
		"long: \"" + value + "\"").initCause(e);
	}
    }

    /**
     * Returns the value of a {@code long} property within a bound range of
     * values.
     *
     * @param	name the property name
     * @param	defaultValue the default value
     * @param	min the minimum value to allow
     * @param	max the maximum value to allow
     * @return	the value
     * @throws	IllegalArgumentException if the value or {@code defaultValue}
     *		is less than {@code min} or greater than {@code max}, or if
     *		{@code min} is greater than {@code max}
     * @throws	NumberFormatException if the value does not contain a parsable
     *		<code>long</code>
     */
    public long getLongProperty(
	String name, long defaultValue, long min, long max)
    {
	if (min > max) {
	    throw new IllegalArgumentException(
		"The min must not be greater than the max");
	} else if (min > defaultValue || defaultValue > max) {
	    throw new IllegalArgumentException(
		"The default value must be between the min and the max");
	}
	long result = getLongProperty(name, defaultValue);
	if (min > result) {
	    throw new IllegalArgumentException(
		"The value of the " + name + " property must not be less " +
		"than " + min + ": " + result);
	} else if (result > max) {
	    throw new IllegalArgumentException(
		"The value of the " + name + " property must not be greater " +
		"than " + max + ": " + result);
	}
	return result;
    }

    /**
     * Returns an instance of the class whose fully qualified class name is
     * specified by a property, and that has a constructor with the specified
     * parameters.  The class should extend or implement the specified type,
     * and not be abstract.
     *
     * @param	<T> the type of the return value
     * @param	name the property name
     * @param	type the class which the return value should be an instance of
     * @param	paramTypes the constructor parameter types
     * @param	args the arguments to pass to the constructor
     * @return	the new instance or <code>null</code> if the property is not
     *		found
     * @throws	IllegalArgumentException if the property is found and a problem
     *		occurs creating the instance, or if the constructor throws a
     *		checked exception
     */
    public <T> T getClassInstanceProperty(
	String name, Class<T> type, Class<?>[] paramTypes, Object... args)
    {
	String className = properties.getProperty(name);
	if (className == null) {
	    return null;
	}
	try {
	    return Class.forName(className)
		.asSubclass(type)
		.getConstructor(paramTypes)
		.newInstance(args);
	} catch (ClassNotFoundException e) {
	    throw new IllegalArgumentException(
		"The class specified by the " + name + " property was not " +
		"found: " + className,
		e);
	} catch (ClassCastException e) {
	    throw new IllegalArgumentException(
		"The class specified by the " + name + " property does not " +
		"implement " + type.getName() + ": " + className,
		e);
	} catch (NoSuchMethodException e) {
	    StringBuilder sb = new StringBuilder();
	    boolean first = true;
	    for (Class<?> paramType : paramTypes) {
		if (first) {
		    first = false;
		} else {
		    sb.append(", ");
		}
		sb.append(paramType.getName());
	    }
	    throw new IllegalArgumentException(
		"The class specified by the " + name + " property, " +
		className + ", does not have a constructor with required " +
		"parameters: " + sb,
		e);
	} catch (InvocationTargetException e) {
	    Throwable cause = e.getCause();
	    if (cause instanceof RuntimeException) {
		throw (RuntimeException) cause;
	    } else if (cause instanceof Error) {
		throw (Error) cause;
	    } else {
		throw new IllegalArgumentException(
		    "Problem calling the constructor for the class " +
		    "specified by the " + name + " property: " +
		    className + ": " + cause,
		    cause);
	    }
	} catch (Exception e) {
	    throw new IllegalArgumentException(
		"Problem creating an instance of the class specified by the " +
		name + " property: " + className + ": " + e,
		e);
	}
    }
    
    /**
     * Parse a property containing property value pairs. The specified property
     * is parsed as a ":" separated list, with each pair of elements treated as
     * a property key and value. Each pair is added to a properties object by
     * calling {@link Properties#setProperty Properties.setProperty}. The
     * resulting properties object is returned.
     * @param name the property name
     * @return a {@code Properties} object
     * @throws IllegalArgumentException if {@code name} is {@code null},
     * or the property is not found,
     * or the embedded property list is malformed.
     */
    public Properties getEmbeddedProperties(String name)
            throws IllegalArgumentException
    {
        if (name == null)
            throw new IllegalArgumentException("name can not be null");
        final String arg = getProperty(name);
        if (arg == null)
            throw new IllegalArgumentException(
                    "The " + name + " property must be specified");
        final String[] propAndValue = arg.split(":");
        if ((propAndValue.length % 2) != 0)
            throw new IllegalArgumentException(
                    "Missmatch propery value pair: " + arg);
        final Properties prop = new Properties();
        for (int i = 0; i < propAndValue.length;) {
            prop.setProperty(propAndValue[i], propAndValue[i+1]);
            i += 2;
        }
        return prop;
    }
}	
