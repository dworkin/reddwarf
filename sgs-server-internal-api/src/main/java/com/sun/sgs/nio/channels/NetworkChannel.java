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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Set;

/**
 * A channel to a network socket.
 * <p>
 * A channel that implements this interface is a channel to a network
 * socket. The {@link #bind} method is used to bind the socket to a local
 * {@link SocketAddress address}, the {@link #getLocalAddress} method
 * returns the address that the socket is bound to, and the
 * {@link #setOption} and {@link #getOption} methods are used to set and
 * query socket options. An implementation of this interface should specify
 * the socket options that it supports.
 * <p>
 * The {#link #bind} and {@link #setOption} methods that do not otherwise
 * have a value to return are specified to return the network channel upon
 * which they are invoked. This allows method invocations to be chained.
 * Implementations of this interface should specialize the return type so
 * that method invocations on the implementation class can be chained.
 */
public interface NetworkChannel extends Channel {

    /**
     * Binds the channel's socket to a local address.
     * <p>
     * This method is used to establish an association between the socket
     * and a local address. Once an association is established then the
     * socket remains bound until the channel is closed. An attempt to bind
     * a socket that is already bound throws {@link AlreadyBoundException}.
     * If the {@code local} parameter has the value {@code null} then the
     * socket will be bound to an address that is assigned automatically.
     * <p>
     * An implementation of this interface should specify if a permission is
     * required when a security manager is installed.
     * 
     * @param local the address to bind the socket, or {@code null} to bind the
     *        socket to an automatically assigned socket address
     * @return this channel
     * @throws AlreadyBoundException if the socket is already bound
     * @throws UnsupportedAddressTypeException if the type of the given
     *         address is not supported
     * @throws ClosedChannelException if the channel is closed
     * @throws IOException if some other I/O error occurs
     */
    NetworkChannel bind(SocketAddress local) throws IOException;

    /**
     * Returns the socket address that this channel's socket is bound to, or
     * {@code null} if the socket is not bound.
     * 
     * @return the socket address that the socket is bound to, or {@code null}
     *         if the channel is not {@link Channel#isOpen() open}
     *         or the channel's socket is not bound
     * @throws IOException if an I/O error occurs
     */
    SocketAddress getLocalAddress() throws IOException;

    /**
     * Sets the value of a socket option.
     * <p>
     * The {@code name} parameter is the name of the socket option.
     * The {@code value} parameter is the value of the option and is of the
     * {@link SocketOption#type() type} specified by the option. A value of
     * {@code null} may be a valid value for some socket options.
     * 
     * @param name the name of the socket option
     * @param value the value of the socket option
     * @return this channel
     * @throws IllegalArgumentException If the socket option is not
     *         supported by this channel, or the value is not a valid value
     *         for this socket option
     * @throws ClosedChannelException if this channel is closed
     * @throws IOException if an I/O error occurs
     * @see SocketOption
     */
    NetworkChannel setOption(SocketOption name, Object value)
        throws IOException;

    /**
     * Returns the value of a socket option.
     * <p>
     * The return type is specific to the socket option and {@code null}
     * may be a valid value for some socket options.
     * 
     * @param name the socket option
     * @return the value of the socket option
     * @throws IllegalArgumentException if the socket option is not
     *         supported by this channel
     * @throws ClosedChannelException if this channel is closed
     * @throws IOException if an I/O error occurs
     * @see SocketOption
     */
    Object getOption(SocketOption name) throws IOException;

    /**
     * Returns a set of the socket options supported by this channel.
     * <p>
     * This method will continue to return the set of options even after the
     * channel has been closed.
     * 
     * @return a set of the socket options supported by this channel
     */
    Set<SocketOption> options();
}
