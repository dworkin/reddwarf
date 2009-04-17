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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.management.ConfigMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

/**
 * The configuration manager for this node.  This object contains
 * various configuration values. Services and components fill in
 * portions of this object with their defaults or overridden values.
 * <p>
 * The ConfigMXBean presented to JMX clients is immutable.
 */
public class ConfigManager implements ConfigMXBean {

    private final NodeType nodeType;
    private final String appName;
    private final String appRoot;
    private final String appListener;
    private final String hostName;
    private final String serverHost;
    private int jmxPort;
    private long standardTxnTimeout;
    private String protocolDesc;

    /** 
     * Creates a config manager instance.
     * @param props  properties
     */
    public ConfigManager(Properties props) {
        String value = props.getProperty(StandardProperties.NODE_TYPE);
        if (value == null) {
            // Default is single node
            // Note - this default is specified by the implementation
            // of the Kernel, in the static method filterProperties.
            // It'd be better if the code wasn't duplicated here.
            nodeType = NodeType.singleNode;
        } else {
            nodeType = NodeType.valueOf(value);
        }
        
        appName = props.getProperty(StandardProperties.APP_NAME);
        appRoot = props.getProperty(StandardProperties.APP_ROOT);
        appListener = props.getProperty(StandardProperties.APP_LISTENER);
        serverHost = props.getProperty(StandardProperties.SERVER_HOST, "none");
        String name;
        try {
            name = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            name = "unknown";
        }
        hostName = name;
    }

    /** {@inheritDoc} */
    public NodeType getNodeType() {
        return nodeType;
    }

    /** {@inheritDoc} */
    public String getAppName() {
        return appName;
    }

    /** {@inheritDoc} */
    public String getAppRoot() {
        return appRoot;
    }

    /** {@inheritDoc} */
    public String getAppListener() {
        return appListener;
    }

    /** {@inheritDoc} */
    public String getHostName() {
        return hostName;
    }
    
    /** {@inheritDoc} */
    public String getServerHostName() {
        return serverHost;
    }
    
    /** {@inheritDoc} */
    public int getJmxPort() {
        return jmxPort;
    }
    
    /** {@inheritDoc} */
    public long getStandardTxnTimeout() {
        return standardTxnTimeout;
    }

    /** {@inheritDoc} */
    public String getProtocolDescriptor() {
	return protocolDesc;
    }
    
    /**
     * Sets the standard timeout value.
     * 
     * @param timeout the standard timeout
     */
    public void setStandardTxnTimeout(long timeout) {
        standardTxnTimeout = timeout;
    }
    
    /**
     * Sets the jmxPort value.
     * 
     * @param jmxPort the port JMX is listening on
     */
    public void setJmxPort(int jmxPort) {
        this.jmxPort = jmxPort;
    }
    
    /**
     * Sets the protocol descriptor.
     *
     * @param	desc the protocol descriptor
     */
    public void setProtocolDescriptor(String desc) {
	this.protocolDesc = desc;
    }
}
