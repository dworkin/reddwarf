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

import com.sun.sgs.impl.protocol.simple.SimpleSgsProtocolDescriptor;
import com.sun.sgs.protocol.ProtocolDescriptor;
import com.sun.sgs.transport.TransportDescriptor;

/**
 * A protocol descriptor with primary and secondary transport descriptors.
 * A protocol may use this class directly or extend it, overriding methods
 * for protocol and/or transport-specific needs.
 */
class MultiSgsProtocolDescriptor extends SimpleSgsProtocolDescriptor {
    
    private static final long serialVersionUID = 1L;

    /** The secondary transport descriptor. */
    private final TransportDescriptor secondaryDesc;   

    /**
     * Constructs an instance with the given transport descriptors.
     *
     * @param	primaryDesc primary transport descriptor
     * @param	secondaryDesc secondary transport descriptor
     */
    MultiSgsProtocolDescriptor(TransportDescriptor primaryDesc,
                               TransportDescriptor secondaryDesc)
    {
	super(primaryDesc);
        assert secondaryDesc != null;
        this.secondaryDesc = secondaryDesc;
    }

    /** {@inheritDoc}
     *
     * <p>This implementation returns {@code true} if the specified {@code
     * descriptor} is an instance of this class and this descriptor's
     * underlying primary and secondary transport descriptors are
     * compatible with (respectively) the specified {@code descriptor}'s
     * primary and secondary transport descriptors.
     */
    public boolean supportsProtocol(ProtocolDescriptor descriptor) {
        if (!(descriptor instanceof MultiSgsProtocolDescriptor)) {
            return false;
	}
        
        MultiSgsProtocolDescriptor desc =
	    (MultiSgsProtocolDescriptor) descriptor;
        
        return transportDesc.supportsTransport(desc.transportDesc) &&
               secondaryDesc.supportsTransport(desc.secondaryDesc);
    }

    /**
     * {@inheritDoc}
     */
    public byte[] getConnectionData() {
	byte[] primaryData = transportDesc.getConnectionData();
	byte[] secondaryData = secondaryDesc.getConnectionData();
	byte[] redirectionData =
	    new byte[primaryData.length + secondaryData.length];
	System.arraycopy(primaryData, 0, redirectionData, 0, primaryData.length);
	System.arraycopy(secondaryData, 0, redirectionData,
			 primaryData.length, secondaryData.length);
	return redirectionData;
    }
}
