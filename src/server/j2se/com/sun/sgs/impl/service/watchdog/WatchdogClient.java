/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import java.io.IOException;
import java.rmi.Remote;
import java.util.Collection;

/**
 * A remote interface for callbacks from the Watchdog server.
 */
public interface WatchdogClient extends Remote {

    /**
     * Notifies this client that the nodes specified by corresponding
     * information in the {@code ids}, {@code hosts}, and {@code
     * status} arrays have a status change ({@code true} for alive,
     * and {@code false} for failed).
     *
     * @param	ids an array of node IDs
     * @param	hosts an array of host names
     * @param	status an array of status 
     *
     * @throws	IllegalArgumentException if array lengths don't match
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void nodeStatusChanges(long[] ids, String[] hosts, boolean[] status)
	throws IOException;
}
