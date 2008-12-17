/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.transport;

import com.sun.sgs.app.Delivery;
import java.io.Serializable;

/**
 * Transport descriptor. Classes that implement {@code TransportDescriptor}
 * must also implement {@link Serializable}.
 */
public interface TransportDescriptor {
    
    /**
     * Get supported delivery guarantees for the transport.
     * @return the supported delivery guarantees for the transport
     */
    Delivery[] getSupportedDelivery();
    
    /**
     * Check if the transport supports the requested delivery guarantee.
     * @param required the required delivery guarantee
     * @return {@code true} if the transport supports the {@code required}
     * delivery guarantee, and {@code false} otherwise
     */
    boolean canSupport(Delivery required);

    /**
     * Check if the specified transport is compatible with the transport this
     * descriptor represents. The comparison is transport specific.
     * @param descriptor to compare
     * @return {@code true} if the specified descriptor represents a transport
     * compatible with the transport this descriptor represents, and
     * {@code false} otherwise
     */
    boolean isCompatibleWith(TransportDescriptor descriptor);
    
    /**
     * Return the transport specific connection data as a byte array.
     * @return the connection data
     */
    byte[] getConnectionData();
}