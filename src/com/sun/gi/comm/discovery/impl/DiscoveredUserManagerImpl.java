package com.sun.gi.comm.discovery.impl;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

import com.sun.gi.comm.discovery.DiscoveredUserManager;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class DiscoveredUserManagerImpl implements DiscoveredUserManager {

    String clientClass;

    Map<String,String> parameters = new HashMap<String,String>();

    /**
     * DiscoveredUserManagerImpl
     *
     * @param clientClass  the name of the class to instantiate
     *                     as the UserManagerClient
     */
    public DiscoveredUserManagerImpl(String clientClass) {
	this.clientClass = clientClass;
    }

    public String getClientClass() {
	return clientClass;
    }

    /**
     * addParameter
     *
     * @param tag String
     * @param value String
     */
    public void addParameter(String tag, String value) {
	parameters.put(tag, value);
    }

    /**
     * getParameter
     *
     * @param tag String
     * @return String
     */
    public String getParameter(String tag) {
	return parameters.get(tag);
    }

    /**
     * getParameters
     *
     * @return the parameters for this endpoint
     */
    public Map<String, String> getParameters() {
	return Collections.unmodifiableMap(parameters);
    }
}
