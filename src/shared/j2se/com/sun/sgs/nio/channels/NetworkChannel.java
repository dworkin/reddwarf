package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Set;

public interface NetworkChannel extends Channel {

    /**
     * Binds the channel's socket to a local address.
     * <p>
     * This method is used to establish an association between the socket
     * and a local address. Once an association is established then the
     * socket remains bound until the channel is closed. An attempt to bind
     * a socket that is already bound throws AlreadyBoundException. If the
     * local parameter has the value null then the socket will be bound to
     * an address that is assigned automatically.
     * 
     * @param local the address to bind the socket, or null to bind the
     *        socket to an automatically assigned socket address
     * @return this channel
     * @throws AlreadyBoundException if the socket is already bound
     * @throws UnsupportedAddressTypeException if the type of the given
     *         address is not supported
     * @throws SecurityException if a security manager has been installed
     *         and its checkListen method denies the operation
     * @throws ClosedChannelException if the channel is closed
     * @throws IOException if some other I/O error occurs
     */
    NetworkChannel bind(SocketAddress local) throws IOException;

    /**
     * Returns the socket address that this channel's socket is bound to, or
     * null if the socket is not bound.
     * 
     * @return the local address; null if the socket is not bound
     * @throws IOException if an I/O error occurs
     */
    SocketAddress getLocalAddress() throws IOException;

    /**
     * Sets the value of a socket option.
     * <p>
     * The name parameter is the name of the socket option. The value
     * parameter is the value of the option and is of the type specified by
     * the option. A value of null may be a valid value for some socket
     * options.
     * 
     * @param name the name of the socket option
     * @param value the value of the socket option
     * @return this channel
     * @throws UnsupportedSocketOptionException if the socket option is not
     *         supported by this channel
     * @throws IllegalArgumentException if the value of the socket option is
     *         not of the correct type or not a valid value for the socket
     *         option
     * @throws ClosedChannelException if this channel is closed
     * @throws IOException if an I/O error occurs
     * @see SocketOption
     */
    NetworkChannel setOption(SocketOption name, Object value)
        throws IOException;

    /**
     * Returns the value of a socket option.
     * 
     * @param name the name of the socket option
     * @return the value of the socket option
     * @throws UnsupportedSocketOptionException if the socket option is not
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
