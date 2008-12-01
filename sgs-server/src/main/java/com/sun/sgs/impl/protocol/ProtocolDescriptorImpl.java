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

package com.sun.sgs.impl.protocol;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.protocol.ProtocolDescriptor;
import com.sun.sgs.transport.TransportDescriptor;
import java.io.Serializable;

/**
 * Base protocol descriptor class. A protocl may use this class directly
 * or extend it, overring methods for transport specific needs. For delivery
 * related methods, {@code getSupportedDelivery} and {@code canSupport}
 * this class defers to the transport descriptor.
 */
public class ProtocolDescriptorImpl implements ProtocolDescriptor,
                                               Serializable
{
    private static final long serialVersionUID = 1L;

    private final String type;
    private final TransportDescriptor transportDesc;   
        
    /**
     * Constructor.
     * @param type type of protocol
     * @param transportDesc transport descriptor
     */
    public ProtocolDescriptorImpl(String type,
                                  TransportDescriptor transportDesc)
    {
        assert type != null;
        assert transportDesc != null;
        this.type = type;
        this.transportDesc = transportDesc;
    }
    
    @Override
    public String getType() {
        return type;
    }

    @Override
    public Delivery[] getSupportedDelivery() {
        return transportDesc.getSupportedDelivery();
    }

    @Override
    public boolean canSupport(Delivery required) {
        return transportDesc.canSupport(required);
    }

    @Override
    public TransportDescriptor getTransport() {
        return transportDesc;
    }

    @Override
    public boolean isCompatibleWith(ProtocolDescriptor descriptor) {
        if (getType().equals(descriptor.getType())) {
            for (Delivery required : descriptor.getSupportedDelivery()) {
                if (!canSupport(required))
                    return false;
            }
            return getTransport().isCompatibleWith(descriptor.getTransport());
        }
        return false;
    }
}
