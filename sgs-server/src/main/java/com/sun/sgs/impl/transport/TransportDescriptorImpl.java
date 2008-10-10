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

package com.sun.sgs.impl.transport;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.transport.TransportDescriptor;
import java.io.Serializable;

/**
 * Base transport descriptor class. A transport may use this class directly
 * or extend it, overring methods for transport specific needs.
 */
public class TransportDescriptorImpl
        implements TransportDescriptor, Serializable
{
    private static final long serialVersionUID = 1L;

    protected final String type;
    protected final Delivery[] supportedDelivery;
    protected final String hostName;
    protected final int listeningPort;
        
    /**
     * Constructor.
     * @param type transport type
     * @param supportedDelivery supported delivery modes
     * @param hostName host name
     * @param listeningPort port transport is listening on
     */
    protected TransportDescriptorImpl(String type,
                                          Delivery[] supportedDelivery,
                                          String hostName,
                                          int listeningPort) {
        assert type != null;
        assert supportedDelivery != null;
        assert hostName != null;
        this.type = type;
        this.supportedDelivery = supportedDelivery;
        this.hostName = hostName;
        this.listeningPort = listeningPort;
    }
    
    @Override
    public String getType() {
        return type;
    }

    @Override
    public Delivery[] getSupportedDelivery() {
        return supportedDelivery;
    }

    @Override
    public boolean canSupport(Delivery required) {
        for (Delivery delivery : supportedDelivery) {
            if (delivery.equals(required))
                return true;
        }
        return false;
    }

    @Override
    public String getHostName() {
        return hostName;
    }

    @Override
    public int getListeningPort() {
        return listeningPort;
    }

    @Override
    public boolean isCompatibleWith(TransportDescriptor descriptor) {
        if (getType().equals(descriptor.getType())) {
            for (Delivery required : descriptor.getSupportedDelivery()) {
                if (!canSupport(required))
                        return false;
            }
            return true;
        }
        return false;
    }
}
