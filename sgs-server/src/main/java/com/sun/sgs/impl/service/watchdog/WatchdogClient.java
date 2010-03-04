/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.service.Node.Health;
import java.io.IOException;
import java.rmi.Remote;

/**
 * A remote interface for callbacks from the Watchdog server.
 */
public interface WatchdogClient extends Remote {

    /**
     * Notifies this client that the nodes specified by corresponding
     * information in the {@code ids}, {@code hosts}, 
     * {@code health}, and {@code backups} arrays have a health change
     * and may need to
     * recover (if the backup ID is equal to the local node ID). The
     * {@code backups} array is only only consulted if the corresponding
     * element in {@code health} returns {@code false} from
     * {@code Health.isAlive()}. If no node has been
     * assigned as a backup, it is indicated by 
     * {@value com.sun.sgs.impl.service.watchdog.NodeImpl#INVALID_ID}.
     *
     * @param	ids an array of node IDs
     * @param	hosts an array of host names
     * @param	health an array of node health
     * @param	backups an array of backup node IDs
     *
     * @throws	IllegalArgumentException if array lengths don't match
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void nodeStatusChanges(long[] ids, String[] hosts,
			   Health[] health, long[] backups)
	throws IOException;

    /**
     * Mechanism to report a failure to the Watchdog service. This is called
     * by the Watchdog server when a remote node is instructed to shutdown.
     * This method calls the node's local shutdown process to start the
     * shutdown procedure.
     * 
     * @param className the class which reported the failure
     * @throws IOException if a communication problem occurs while invoking
     * this method
     */
    void reportFailure(String className) throws IOException;
}
