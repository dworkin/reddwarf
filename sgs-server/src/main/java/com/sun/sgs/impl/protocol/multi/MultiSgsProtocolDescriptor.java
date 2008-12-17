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

package com.sun.sgs.impl.protocol.multi;

import com.sun.sgs.service.ProtocolDescriptor;
import com.sun.sgs.transport.TransportDescriptor;
import java.io.Serializable;

/**
 * Base protocol descriptor class. A protocol may use this class directly
 * or extend it, overriding methods for transport specific needs. For delivery
 * related methods, {@code getSupportedDelivery} and {@code canSupport}
 * this class defers to the transport descriptor.
 */
class MultiSgsProtocolDescriptor implements ProtocolDescriptor, Serializable {
    private static final long serialVersionUID = 1L;

    final TransportDescriptor primaryDesc;
    
    final TransportDescriptor secondaryDesc;   

    /**
     * Constructor.
     * @param transportDesc transport descriptor
     */
    MultiSgsProtocolDescriptor(TransportDescriptor primaryDesc,
                               TransportDescriptor secondaryDesc)
    {
        assert primaryDesc != null;
        assert secondaryDesc != null;
        this.primaryDesc = primaryDesc;
        this.secondaryDesc = secondaryDesc;
    }

    @Override
    public boolean isCompatibleWith(ProtocolDescriptor descriptor) {
        if (!(descriptor instanceof MultiSgsProtocolDescriptor))
            return false;
        
        MultiSgsProtocolDescriptor desc =
                            (MultiSgsProtocolDescriptor)descriptor;
        
        return primaryDesc.isCompatibleWith(desc.primaryDesc) &&
               secondaryDesc.isCompatibleWith(desc.secondaryDesc);
    }
}
