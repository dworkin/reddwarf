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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
     *		occurs creating the instance, or if the constructor throws
     *		a checked exception
     */
    public <T> T getClassInstanceProperty(
	String name, Class<T> type, Class<?>[] paramTypes, Object... args)
    {
	String className = properties.getProperty(name);
	if (className == null) {
	    return null;
	}
	return getClassInstance(name, className, type, paramTypes, args);
    }
    
    /**
     * Returns an instance of the class whose fully qualified class name is
     * specified by a property, and that has a constructor with the specified
     * parameters.  The class should extend or implement the specified type,
     * and not be abstract.
     *
     * @param	<T> the type of the return value
     * @param	name the property name
     * @param   defaultClass  the fully qualified class name to use if the
     *          {@code name} property is not found
     * @param	type the class which the return value should be an instance of
     * @param	paramTypes the constructor parameter types
     * @param	args the arguments to pass to the constructor
     * @return	the new instance or <code>null</code> if the property is not
     *		found and {@code defaultClassName} is {@code null}
     * @throws	IllegalArgumentException if a problem occurs creating the
     *		instance, or if the constructor throws a checked exception
     */
    @SuppressWarnings("unchecked")
    public <T> T getClassInstanceProperty(
	String name, String defaultClass, Class<T> type,
        Class<?>[] paramTypes, Object... args)
    {
        Object instance =
	    getClassInstanceProperty(name, type, paramTypes, args);
        
        if (instance != null) {
            return (T) instance;
        }
	if (defaultClass == null) {
            return null;
        }
        return getClassInstance(name, defaultClass, type, paramTypes, args);
    }
    
    /**
     * Returns an instance of the class whose fully qualified class name is
     * specified by {@code className}, and that has a constructor with the
     * specified parameters.  The class should extend or implement the
     * specified type, and not be abstract.
     *
     * @param	<T> the type of the return value
     * @param	name the property name (used for exception message text),
     *		or {@code null}
     * @param	className the class name
     * @param	type the class which the return value should be an instance of
     * @param	paramTypes the constructor parameter types
     * @param	args the arguments to pass to the constructor
     * @return	the new instance
     * @throws	IllegalArgumentException if a problem occurs creating the
     *		instance, or if the constructor throws a checked exception
     */
    private <T> T getClassInstance(
 	String name, String className, Class<T> type, Class<?>[] paramTypes,
	Object... args)
    {
        if (className == null) {
            throw new NullPointerException("null className");
        }
	try {
	    return Class.forName(className)
		.asSubclass(type)
		.getConstructor(paramTypes)
		.newInstance(args);
	} catch (ClassNotFoundException e) {
	    throw new IllegalArgumentException(
		"The class " + className + getPropertyText(name) +
		" was not found",
		e);
	} catch (ClassCastException e) {
	    throw new IllegalArgumentException(
		"The class " + className + getPropertyText(name) +
		" does not implement " + type.getName(),
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
		"The class " + className + getPropertyText(name) +
		" does not have a constructor with required parameters: " + sb,
		e);
	} catch (InvocationTargetException e) {
	    Throwable cause = e.getCause();
	    if (cause instanceof RuntimeException) {
		throw (RuntimeException) cause;
	    } else if (cause instanceof Error) {
		throw (Error) cause;
	    } else {
		throw new IllegalArgumentException(
		    "Calling the constructor for the class " +
		    className + getPropertyText(name) + " throws: " + cause,
		    cause);
	    }
	} catch (Exception e) {
	    throw new IllegalArgumentException(
		"Creating an instance of the class " +
		className + getPropertyText(name) + " throws: " + e,
		e);
	}
    }

    /**
     * Returns a formatted string containing the property {@code name} (if
     * it is non-null}, otherwise returns an empty string.
     *
     * @param	name a property name or {@code null}
     * @return	if {@code name} is non-null, a formatted string containing
     *		the property name; otherwise, an empty string
     */
    private String getPropertyText(String name) {
	return
	    name != null ?
	    ", specified by the property: " + name + "," :
	    "";
    }

    /**
     * Returns the value of an {@link Enum} property.
     *
     * @param	<T> the enumeration type
     * @param	name the property name
     * @param	enumType the enumeration type
     * @param	defaultValue the default value
     * @return	the value
     * @throws	IllegalArgumentException if the value does not name a constant
     *		of the enumeration type
     */
    public <T extends Enum<T>> T getEnumProperty(
	String name, Class<T> enumType, T defaultValue)
    {
	Objects.checkNull("name", name);
	Objects.checkNull("enumType", enumType);
	Objects.checkNull("defaultValue", defaultValue);
	String value = properties.getProperty(name);
	if (value == null) {
	    return defaultValue;
	}
	try {
	    return Enum.valueOf(enumType, value);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgumentException(
		"The value of the " + name + " property was \"" + value +
		"\", but must be one of: " +
		Arrays.toString(enumType.getEnumConstants()));
	}
    }

    /**
     * Returns a list of objects of the type specified by {@code type}.  The
     * objects are created from the property with the given {@code name} from
     * the backing set of properties.  The property is assumed to be a colon
     * separated list of {@code String}s, and each element in the list is
     * instantiated by calling the given type's {@code Constructor} with a
     * single {@code String} parameter.
     * If any of the {@code String}s in the colon separated list is an empty
     * string,
     * the value of {@code defaultElement} will be used for that item in the
     * returned {@code List}.  If the property with the given {@code name} is
     * not found, an empty list will be returned.
     *
     * @param <T> the type of the objects in the returned {@code List}
     * @param name the property name
     * @param type the class which each object in the returned {@code List}
     *             should be an instance of
     * @param defaultElement the default value to use if an empty {@code String}
     *                       is one of the items in the list
     * @return a list of objects of the given type represented by the property
     *         with the given name, or an empty list if the property is not
     *         found
     * @throws IllegalArgumentException if a problem occurs creating an instance
     *         of the given class type, or the class type does not have a
     *         constructor that takes a single {@code String} parameter
     */
    public <T> List<T> getListProperty(String name,
                                       Class<T> type,
                                       T defaultElement) {
        Objects.checkNull("name", name);
        Objects.checkNull("type", type);

        List<T> list = new ArrayList<T>();
        String value = properties.getProperty(name);
        if (value == null) {
            return list;
        }

        String[] values = value.split(":", -1);
        Class<?>[] constructorParams = new Class<?>[]{String.class};
        Constructor<T> constructor = null;
        try {
            constructor = type.getConstructor(constructorParams);
        } catch (NoSuchMethodException nsme) {
            throw new IllegalArgumentException(
                        "The class " + type.getName() + " does not have a " +
                        "constructor with the required parameter : String",
                        nsme);
        }
        for (String v : values) {
            if (v.equals("")) {
                list.add(defaultElement);
                continue;
            }

            try {
                list.add(constructor.newInstance(v));
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Creating an instance of the class " + type.getName() +
                        " throws: " + e, e);
            }
        }

        return list;
    }

    /**
     * Returns a list of {@code Enum}s of the type specified by
     * {@code enumType}.  The
     * objects are created from the property with the given {@code name} from
     * the backing set of properties.  The property is assumed to be a colon
     * separated list of {@code String}s, and each element in the list is
     * instantiated by calling the
     * {@link Enum#valueOf(java.lang.Class, java.lang.String) Enum.valueOf}
     * method on each respective {@code String} in the list.
     * If any of the {@code String}s in the colon separated list is an empty
     * string,
     * the value of {@code defaultElement} will be used for that item in the
     * returned {@code List}.  If the property with the given {@code name} is
     * not found, an empty list will be returned.
     *
     * @param <T> the {@code Enum} type of the objects in the returned
     *            {@code List}
     * @param name the property name
     * @param enumType the {@code Enum} class which each object in the returned
     *                 {@code List} should be an instance of
     * @param defaultElement the default value to use if an empty {@code String}
     *                     is one of the items in the list
     * @return a list of {@code Enum} objects of the given type represented by
     *         the property with the given name
     * @throws IllegalArgumentException if any of the items in the list is
     *         not a valid value for the given {@code Enum} type
     */
    public <T extends Enum<T>> List<T> getEnumListProperty(String name,
                                                           Class<T> enumType,
                                                           T defaultElement) {
        Objects.checkNull("name", name);
	Objects.checkNull("enumType", enumType);

        List<T> list = new ArrayList<T>();
        String value = properties.getProperty(name);
        if (value == null) {
            return list;
        }

        String[] values = value.split(":", -1);
        for (String v : values) {
            if (v.equals("")) {
                list.add(defaultElement);
                continue;
            }

            try {
                list.add(Enum.valueOf(enumType, v));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "A value in the list of items in the " + name +
                        " property was \"" + v +
                        "\", but must be one of: " +
                        Arrays.toString(enumType.getEnumConstants()));
            }
        }

        return list;
    }

    /**
     * Returns a {@code List} of {@link Class} typed objects.  The objects
     * in the list are created from the property with the given {@code name}
     * from the backing set of properties.  The property is assumed to be a
     * colon separated list of Strings, where each String is a fully qualified
     * class name of a class.  Any empty String appearing in the list
     * will be added to the returned list as a {@code null}.  If the property
     * with the given {@code name} is not found, an empty list will be returned.
     *
     * @param name the name of the property
     * @return a list of {@code Class} objects represented by the property with
     *         the given name
     * @throws IllegalArgumentException if a {@code Class} cannot be found for
     *         any of the items in the list
     */
    public List<Class<?>> getClassListProperty(String name) {
        Objects.checkNull("name", name);

        List<Class<?>> list = new ArrayList<Class<?>>();
        String value = properties.getProperty(name);
        if (value == null) {
            return list;
        }

        String[] values = value.split(":", -1);
        for (String v : values) {
            if (v.equals("")) {
                list.add(null);
                continue;
            }

            try {
                list.add(Class.forName(v));
            } catch (ClassNotFoundException cnfe) {
                throw new IllegalArgumentException(
                        "A value in the list of items in the " + name +
                        " property was \"" + v +
                        "\", but a class was not found for this value", cnfe);
            }
        }

        return list;
    }
}	
