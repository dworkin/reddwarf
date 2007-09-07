/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import java.io.IOException;
import java.rmi.Remote;

/**
 * A remote interface for callbacks from the Watchdog server.
 */
public interface WatchdogClient extends Remote {

    /**
     * Notifies this client that the nodes specified by corresponding
     * information in the {@code ids}, {@code hosts}, {@code status},
     * and {@code backups} arrays have a status change ({@code true}
     * for alive, and {@code false} for failed) and may need to
     * recover (if the backup ID is equal to the local node ID).
     *
     * @param	ids an array of node IDs
     * @param	hosts an array of host names
     * @param	status an array of node status
     * @param	backups an array of backup node IDs
     *
     * @throws	IllegalArgumentException if array lengths don't match
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void nodeStatusChanges(long[] ids, String[] hosts,
			   boolean[] status, long[] backups)
	throws IOException;
}
