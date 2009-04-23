/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.transport.tcp;

import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.transport.TransportDescriptor;
import java.io.Serializable;

/**
 * TCP transport descriptor.
 */
class TcpDescriptor implements TransportDescriptor, Serializable {
    private static final long serialVersionUID = 1L;

    final String hostName;
    final int listeningPort;
        
    /**
     * Constructor.
     * @param hostName host name
     * @param listeningPort port transport is listening on
     */
    TcpDescriptor(String hostName, int listeningPort) {
        if (hostName == null) {
            throw new NullPointerException("null hostName");
        }
        this.hostName = hostName;
        this.listeningPort = listeningPort;
    }

    /** {@inheritDoc} */
    public boolean supportsTransport(TransportDescriptor descriptor) {
        return descriptor instanceof TcpDescriptor;
    }
    
    /**
     * {@inheritDoc}
     *     
     * This method will return a {@code byte} array that contains the
     * following data:
     * <ul>
     * <li> (String) hostname
     * <li> (int) port
     * </ul>
     */
    public byte[] getConnectionData() {
        MessageBuffer buf =
                new MessageBuffer(MessageBuffer.getSize(hostName) + 4);
        buf.putString(hostName).
            putInt(listeningPort);
        return buf.getBuffer();
    }

    /**
     * Returns a string representation of this descriptor.
     *
     * @return	a string representation of this descriptor
     */
    public String toString() {
	return "TCP[host:" + hostName + ", port:" + listeningPort + "]";
    }
}
