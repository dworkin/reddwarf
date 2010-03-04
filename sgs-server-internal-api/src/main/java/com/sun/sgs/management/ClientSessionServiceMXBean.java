/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.management;

import com.sun.sgs.service.ClientSessionService;

/**
 * The management interface for the client session service.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #MXBEAN_NAME}.
 * 
 */
public interface ClientSessionServiceMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs.service:type=ClientSessionService";
    
    // Maybe add number of active clients in the system, number
    // of connects/disconnects?
    // number of channels a client is connected to
    // amount of communications traffic this client sends/receives?
    
    /**
     * Returns the number of times {@link 
     * ClientSessionService#addSessionStatusListener
     * addSessionStatusListener} has been called.
     * 
     * @return the number of times {@code addSessionStatusListener} 
     *         has been called
     */
    long getAddSessionStatusListenerCalls();
    
    /**
     * Returns the number of times {@link 
     * ClientSessionService#getSessionProtocol getSessionProtocol}
     * has been called.
     * 
     * @return the number of times {@code getSessionProtocol} 
     *         has been called
     */
    long getGetSessionProtocolCalls();

    /**
     * Returns the number of times {@link
     * ClientSessionService#isRelocatingToLocalNode
     * isRelocatingToLocalNode} has been called.
     * 
     * @return the number of times {@code isRelocatingToLocalNode} 
     *         has been called
     */
    long getIsRelocatingToLocalNodeCalls();
}
