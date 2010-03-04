/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
