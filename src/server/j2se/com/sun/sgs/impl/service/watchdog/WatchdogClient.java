/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.service.Node;
import java.io.IOException;
import java.rmi.Remote;
import java.util.Collection;

/**
 * A remote interface for callbacks from the Watchdog server.
 */
public interface WatchdogClient extends Remote {

    /**
     * Notifies this client that the specified collection of nodes
     * have a status change (either alive, or failed).
     *
     * @param	nodes a collection of nodes
     *
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void nodeStatusChange(Collection<Node> nodes) throws IOException;
}
