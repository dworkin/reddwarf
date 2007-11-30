package com.sun.sgs.nio.channels;

/**
 * A socket option associated with a socket. A {@link NetworkChannel}
 * defines the {@link NetworkChannel#setOption setOption} and
 * {@link NetworkChannel#getOption getOption} methods to configure and query
 * the socket options of the channel's socket.
 */
public interface SocketOption {

    /**
     * Returns the name of the socket option.
     *
     * @return the name of the socket option
     */
    String name();

    /**
     * Returns the type of the socket option value.
     *
     * @return the type of the socket option value
     */
    Class<?> type();
}
