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

package com.sun.sgs.protocol;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.transport.TransportDescriptor;
import java.io.Serializable;

/**
 * Protocol descriptor. Classes that implement {@code ProtocolDescriptor}
 * must also implement {@link Serializable}.
 */
public interface ProtocolDescriptor {
    
    /**
     * Get the protocol type.
     * @return the protocol type.
     */
    String getType();
    
    /**
     * Get supported delivery guarantees for the protocol.
     * @return the supported delivery guarantees for the protocol
     */
    Delivery[] getSupportedDelivery();
    
    /**
     * Check if the protocol supports the requested delivery guarantee.
     * @param required the required delivery guarantee
     * @return {@code true} if the protocol supports the {@code required}
     * delivery guarantee, and {@code false} otherwise
     */
    boolean canSupport(Delivery required);
    
    /**
     * Return the descriptor for the transport being used by this protocol.
     * @return the transport descriptor
     */
    TransportDescriptor getTransport();
    
    /**
     * Check if the specified protocol is compatible with the protocol this
     * descriptor represents. The comparison is protocol specific.
     * @param descriptor to compare
     * @return {@code true} if the specified descriptor represents a protocol
     * compatible with the protocol this descriptor represents, and
     * {@code false} otherwise
     */
    boolean isCompatibleWith(ProtocolDescriptor descriptor);
}
