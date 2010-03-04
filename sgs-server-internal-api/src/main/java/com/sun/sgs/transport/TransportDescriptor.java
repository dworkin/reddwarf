/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.transport;

import java.io.Serializable;

/**
 * Transport descriptor. Classes that implement {@code TransportDescriptor}
 * must also implement {@link Serializable} to allow instances to be
 * persisted.
 */
public interface TransportDescriptor {

    /**
     * Check if the specified transport is compatible with the transport this
     * descriptor represents.
     * @param descriptor to compare
     * @return {@code true} if the specified descriptor represents a transport
     * compatible with the transport this descriptor represents, and
     * {@code false} otherwise
     */
    boolean supportsTransport(TransportDescriptor descriptor);
    
    /**
     * Return the transport specific connection data as a byte array. The data
     * can be used by a client to connect to a server. Each transport should
     * document the format of the data returned by this method.
     * @return the connection data
     */
    byte[] getConnectionData();
}
