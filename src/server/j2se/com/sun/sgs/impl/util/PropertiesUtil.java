package com.sun.sgs.impl.util;

import java.util.Properties;

/**
 * Defines utilities for working with {@link Properties}.  This class cannot be
 * instantiated.
 */
public final class PropertiesUtil {

    /** This class cannot be instantiated. */
    private PropertiesUtil() {
	throw new AssertionError();
    }

    /**
     * Returns the value of a <code>boolean</code> property.
     *
     * @param	properties the properties
     * @param	name the property name
     * @param	defaultValue the default value
     * @return	the value
     */
    public static boolean getBooleanProperty(
	Properties properties, String name, boolean defaultValue)
    {
	String value = properties.getProperty(name);
	return value == null ? defaultValue : Boolean.valueOf(value);
    }

    /**
     * Returns the value of an <code>int</code> property.
     *
     * @param	properties the properties
     * @param	name the property name
     * @param	defaultValue the default value
     * @return	the value
     * @throws	NumberFormatException if the value does not contain a parsable
     *		<code>int</code>
     */
    public static int getIntProperty(
	Properties properties, String name, int defaultValue)
    {
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
     * Returns the value of an <code>long</code> property.
     *
     * @param	properties the properties
     * @param	name the property name
     * @param	defaultValue the default value
     * @return	the value
     * @throws	NumberFormatException if the value does not contain a parsable
     *		<code>long</code>
     */
    public static long getLongProperty(
	Properties properties, String name, long defaultValue)
    {
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
}	
