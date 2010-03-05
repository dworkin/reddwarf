/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
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
 */

package com.sun.sgs.management;

import com.sun.sgs.kernel.NodeType;

/**
 * The immutable management information containing this node's configuration.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #MXBEAN_NAME}.
 * 
 */
public interface ConfigMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs:type=Config";   
    
    /**
     * Returns the type of this node.
     * 
     * @return the node type
     * 
     */
    NodeType getNodeType();

    /**
     * Returns the application name.
     * @return the application name
     */
    String getAppName();

    /**
     * Returns the application root directory name.
     * 
     * @return the application root directory name
     */
    String getAppRoot();

    /**
     * Returns the application listener class name.
     * 
     * @return the class name of the application listener
     */
    String getAppListener();

    /**
     * Returns the host this node is running on.
     *
     * @return the host this node is running on
     */
    String getHostName();
    
    /** 
     * Returns the server host property.
     * 
     * @return the server host property, which is valid for application nodes
     *         only
     */
    String getServerHostName();

    /**
     * Returns the JMX remote listening port.
     * 
     * @return the port JMX is listening on for remote connections, or 
     *         {@code -1} if no remote JMX connections are supported
     */
    int getJmxPort();
    
    /**
     * Returns the standard transaction timeout, in milliseconds.
     * 
     * @return the standard transaction timeout, in milliseconds
     */
    long getStandardTxnTimeout();

    /**
     * Returns the protocol descriptor, as a {@code String}.
     *
     * @return the protocol descriptor, as a {@code String}
     */
    String getProtocolDescriptor();
}
