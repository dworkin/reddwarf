/*
 * JMEDiscoveredUserManagerImpl.java
 *
 * Created on January 30, 2006, 2:29 PM
 *
 *
 */

package com.sun.gi.comm.discovery.impl;

import java.util.Hashtable;

/**
 *
 * @author as93050
 */
public class JMEDiscoveredUserManagerImpl {
    
    String clientClass;
    Hashtable parameters = new Hashtable();
    /** Creates a new instance of JMEDiscoveredUserManagerImpl */
    public JMEDiscoveredUserManagerImpl(String clientClass) {
        this.clientClass = clientClass;
    }
        
    /**
     * getClientClass
     *
     * @return String
     */
    public String getClientClass() {
        return clientClass;
    }
    
    /**
     * getParameter
     *
     * @param tag String
     * @return String
     */
    public String getParameter(String tag) {
        return (String)parameters.get(tag);
    }
    
    /**
     * addParameter
     *
     * @param tag String
     * @param value String
     */
    public void addParameter(String tag, String value) {
        parameters.put(tag,value);
    }
}
