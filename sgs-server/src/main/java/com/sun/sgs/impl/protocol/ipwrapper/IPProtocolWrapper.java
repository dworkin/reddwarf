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

package com.sun.sgs.impl.protocol.ipwrapper;

import com.sun.sgs.impl.protocol.simple.SimpleSgsProtocolImpl;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.protocol.ProtocolListener;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Logger;

/**
 * Wrapper for the SimpleSgsProtocol implementation. Will save the socket
 * address of the client connection.
 */
public class IPProtocolWrapper extends SimpleSgsProtocolImpl {

    private static final LoggerWrapper staticLogger = new LoggerWrapper(
	Logger.getLogger(IPProtocolWrapper.class.getName()));
 
    final private InetSocketAddress isa;

    IPProtocolWrapper(ProtocolListener listener,
                      IPProtocolAcceptor acceptor,
                      AsynchronousByteChannel byteChannel,
                      int readBufferSize)
        throws Exception
    {
	super(listener, acceptor, byteChannel, readBufferSize, staticLogger);
	if (byteChannel instanceof AsynchronousSocketChannel) {

            SocketAddress sa =
                 ((AsynchronousSocketChannel)byteChannel).getConnectedAddress();

            if (sa instanceof InetSocketAddress) {
                isa = (InetSocketAddress)sa;
                scheduleReadOnReadHandler();
                return;
            }
        }
        throw new Exception("Channel is not an IP channel");
    }

    /**
     * Get the IP address of the socket for this instance.
     *
     * @return an IP address
     */
    InetAddress getInetAddress() {
        return isa.getAddress();
    }
}
