/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.system;

import java.util.Properties;
import java.util.Set;
import java.util.HashSet;
import java.util.Enumeration;
import java.io.InputStream;
import java.io.IOException;

/**
 * Extension of {@link Properties} that provides automatic support for
 * substitutions of variables.  For example: <p>
 * 
 * PROP1=value<br>
 * PROP2=${PROP1}<br><br>
 * 
 * When loading the above set of properties from a file, PROP2 will
 * automatically substitute PROP1 for its value. <p>
 * 
 * Additional usage requirements:
 * <ul>
 * <li>When properties are provided from a file or {@code InputStream} of some
 * sort, properties can appear in any order and all values will be
 * interpolated as expected.</li>
 * <li>If a property is set using the 
 * {@link #setProperty(java.lang.String, java.lang.String)} method, it will
 * <em>not</em> automatically cause its value to be interpolated in other
 * properties where it is included.</li>
 * <li>An automatically interpolated value of another property can be included
 * in a property by surrounding the key of the value with the start delimiter of
 * '${' and the end delimiter of '}'.</li>
 * <li>Property keys can contain any characters except for the end delimiter
 * of '}'.  Inclusion of this character via an escape sequence is not
 * supported.</li>
 * </ul>
 */
public class SubstitutionProperties extends Properties {
    
    private static final long serialVersionUID = 1L;
    private static final String START_KEY = "${";
    private static final String END_KEY = "}";
    
    /**
     * Creates an empty property list with no defaults.
     */
    public SubstitutionProperties() {
        super();
    }
    
    /**
     * Creates an empty property list with a default backing of properties.
     * When initialized with this constructor, any properties in the
     * default backing that contain substitutable values (i.e. ${PROPNAME})
     * are replaced and set with their interpolated value in the new
     * {@code SubstitutionProperties} object.
     * 
     * @param p default properties
     */
    public SubstitutionProperties(Properties p) {
        super(p);
        replaceAll();
    }
    

    /**
     * Load properties from an {@code InputStream}.  The properties are loaded
     * by calling {@code super.load(inStream);}  Additionally, the loaded
     * properties are filtered and any properties that contain substitutable 
     * variables (i.e. ${PROPNAME}) are replaced and set with their
     * interpolated value.
     * 
     * @param inStream the input stream to load the properties from
     * @throws IOException if an error occurred while reading from the stream
     * @see java.util.Properties#load(java.io.InputStream) 
     */
    public void load(InputStream inStream) 
            throws IOException {
        super.load(inStream);
        replaceAll();
    }
    
    /**
     * Load properties from an XML file specified by the given 
     * {@code InputStream}.  The properties are loaded
     * by calling {@code super.loadFromXML(inStream);}  Additionally, the loaded
     * properties are filtered and any properties that contain substitutable 
     * variables (i.e. ${PROPNAME}) are replaced and set with their
     * interpolated value.
     * 
     * @param inStream the input stream to load the properties from
     * @throws IOException if an error occurred while reading from the stream
     * @see java.util.Properties#loadFromXML(java.io.InputStream) 
     */
    public void loadFromXML(InputStream inStream) 
            throws IOException {
        super.load(inStream);
        replaceAll();
    }
    
    /**
     * Sets the given property by calling the 
     * {@code super.setProperty(name, value)} method.  Additionally, the
     * property is filtered and if it contains subtitutable variables
     * (i.e. ${PROPNAME}), they are replaced and set with their interpolated
     * value.
     * 
     * @param name the name of the property
     * @param value the value of the property
     * @return the previous value of the property, or {@code null} if it was
     *         not set
     * @see java.util.Properties#setProperty(java.lang.String, java.lang.String)
     */
    public Object setProperty(String name, String value) {
        String prev = super.getProperty(name);
        super.setProperty(name, value);
        replace(name, new HashSet<String>());
        return prev;
    }
    
    /**
     * Walks through each of the properties and replaces any instances
     * of ${PROPNAME} with the value of PROPNAME if it exists.  If such
     * a string is found in a property and and the property to lookup does
     * not exist, it is replaced with the empty string.
     */
    private void replaceAll() {
        for (Enumeration<?> e = this.propertyNames();
                e.hasMoreElements(); )  {
            String property = (String) e.nextElement();
            replace(property, new HashSet<String>());
        }
    }
    
    /**
     * Replaces any instances of ${PROPNAME} in the property with key 
     * propName with the value of PROPNAME if it exists.
     * 
     * @param propName property name to replace
     * @param alreadyUsed set of properties currently in the state of
     *                    being interpolated so that we can detect loops
     * @throws IllegalStateException if a loop is detected during substitution
     *                               or if a value contains an opening key
     *                               for substitution '${' but not a closing
     *                               key '}'
     */
    private String replace(String propName, Set<String> beingInterpolated) {
        beingInterpolated.add(propName);
        String propValue = super.getProperty(propName);
        if (propValue == null || propValue.equals("")) {
            return "";
        }
        
        //walk through the value, building a new string and replacing
        //properties as we go
        StringBuilder newValue = new StringBuilder(propValue.length());
        int currentIndex = 0;
        for (int startIndex = propValue.indexOf(START_KEY); startIndex != -1;
                startIndex = propValue.indexOf(START_KEY, currentIndex)) {
            newValue.append(propValue.substring(currentIndex, startIndex));
            currentIndex = propValue.indexOf(END_KEY, startIndex);
            if (currentIndex != -1) {
                String subPropName = propValue.substring(startIndex + 2, 
                                                         currentIndex++);
                if (beingInterpolated.contains(subPropName)) {
                    throw new IllegalStateException(
                            "loop detected when interpolating property : " + 
                            propName);
                }
                newValue.append(replace(subPropName, beingInterpolated));
            } else {
                throw new IllegalArgumentException(
                        "illegal property name, '" + END_KEY + "' not found " +
                        "when interpolating property : " + propName);
            }
        }
        newValue.append(propValue.substring(currentIndex, propValue.length()));
        
        beingInterpolated.remove(propName);
        super.setProperty(propName, newValue.toString());
        return newValue.toString();
    }
}
