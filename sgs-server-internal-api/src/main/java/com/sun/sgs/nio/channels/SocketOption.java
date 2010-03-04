/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

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
