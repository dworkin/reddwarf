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

import java.util.Properties;

/**
 * A factory for creating {@link Protocol}  instances for
 * sending protcol messages to and receiving protocol messages.
 * A {@code ProtocolFactory} must have a constructor that takes
 * the following arguments:
 *
 * <ul>
 * <li>{@link java.util.Properties}</li>
 * <li>{@link com.sun.sgs.kernel.ComponentRegistry}</li>
 * <li>{@link com.sun.sgs.service.TransactionProxy}</li>
 * </ul>
 */
public interface ProtocolFactory {
    
    /**
     * Creates a new protocol.
     * The protocol name must resolve to a class that implements
     * {@link Protocol}. The class should be public, not abstract, and should
     * provide a public constructor with a {@link Properties} and
     * {@link ProtocolConnectionHandler} parameter. The newly created protocol
     * object is returned and each call will return a new instance.
     *
     * @param protocolClassName name of the class that implements
     * {@link Protocol}
     * @param properties properties passed to the transport's constructor
     * @param handler handler passed to the transport's constructor
     * @return the protocol object
     * @throws IllegalArgumentException if any argument is {@code null} or if
     * the class specified by {@code protocolClassName} does not implement
     * {@link Protocol}
     * @throws Exception thrown from the protocol's constructor
     */
    Protocol newProtocol(String protocolClassName,
                         Properties properties,
                         ProtocolConnectionHandler handler)
        throws Exception;
}
