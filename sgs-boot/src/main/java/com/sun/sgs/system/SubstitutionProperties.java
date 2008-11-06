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

package com.sun.sgs.system;

import java.util.Properties;
import java.util.Set;
import java.util.HashSet;
import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;

/**
 * Extension of {@link Properties} that provides automatic support for
 * substitutions of variables.  For example: <p>
 * 
 * PROP1=value<br>
 * PROP2=${PROP1}<br><br>
 * 
 * When loading the above set of properties from a file, PROP2 will
 * automatically subsitute PROP1 for its value.
 */
public class SubstitutionProperties extends Properties {
    
    private static String START_KEY = "${";
    private static String END_KEY = "}";
    
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
     * <code>SubstitutionProperties</code> object.
     * 
     * @param p default properties
     */
    public SubstitutionProperties(Properties p) {
        super(p);
        replaceAll();
    }
    

    @Override
    public void load(InputStream inStream) 
            throws IOException {
        super.load(inStream);
        replaceAll();
    }
    
    @Override
    public void load(Reader reader) 
            throws IOException {
        super.load(reader);
        replaceAll();
    }
    
    @Override
    public void loadFromXML(InputStream inStream) 
            throws IOException {
        super.load(inStream);
        replaceAll();
    }
    
    @Override
    public Object setProperty(String name, String value) {
        super.setProperty(name, value);
        replace(name, new HashSet<String>());
        return super.getProperty(name);
    }
    
    /**
     * Walks through each of the properties and replaces any instances
     * of ${PROPNAME} with the value of PROPNAME if it exists.  If such
     * a string is found in a property and and the property to lookup does
     * not exist, it is replaced with the empty string. <p>
     */
    private void replaceAll() {
        Set<String> properties = super.stringPropertyNames();
        for(String p : properties) {
            replace(p, new HashSet<String>());
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
    private String replace(String propName, Set<String> beingInterpolated) 
            throws IllegalStateException {
        beingInterpolated.add(propName);
        String propValue = super.getProperty(propName);
        if(propValue == null || propValue.equals(""))
            return "";
        
        //walk through the value, building a new string and replacing
        //properties as we go
        StringBuffer newValue = new StringBuffer(propValue.length());
        int currentIndex = 0;
        for (int startIndex = propValue.indexOf(START_KEY); startIndex != -1;
                startIndex = propValue.indexOf(START_KEY, currentIndex)) {
            newValue.append(propValue.substring(currentIndex, startIndex));
            currentIndex = propValue.indexOf(END_KEY, startIndex);
            if(currentIndex != -1) {
                String subPropName = propValue.substring(startIndex+2, currentIndex++);
                if(beingInterpolated.contains(subPropName))
                    throw new IllegalStateException("loop detected when interpolating property : "+propName);
                newValue.append(replace(subPropName, beingInterpolated));
            }
            else
                throw new IllegalStateException(propName + " : "+propValue);
        }
        newValue.append(propValue.substring(currentIndex, propValue.length()));
        
        beingInterpolated.remove(propName);
        super.setProperty(propName, newValue.toString());
        return newValue.toString();
    }
}
