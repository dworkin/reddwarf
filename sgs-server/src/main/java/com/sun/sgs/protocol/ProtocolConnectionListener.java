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

/**
 * Interface implemented by objects implementing a protocol connection handler.
 * A protocol connection handler is passed to a
 * {@link ProtocolFactory#newProtocol ProtocolFactory.newProtocol} when
 * creating a protocol.
 * When a new connection is received by the protocol,
 * {@link #newConnection newConnection} is invoked with the new message channel
 * for that connection.
 * 
 * @see ProtocolFactory
 */
public interface ProtocolConnectionListener {

    /**
     * Notify the handler that a new connection has been initiated. If an
     * exception is thrown the connection will be refused.
     * @param channel for sending protocol messages
     * @param descriptor of the protocol on which the connection is made
     * @return handler to receive protocol messages
     * @throws Exception if the connection is to be refused
     */
    ProtocolHandler newConnection(ProtocolConnection channel,
                                 ProtocolDescriptor descriptor)
        throws Exception;
}
