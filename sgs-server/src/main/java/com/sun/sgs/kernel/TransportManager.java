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

package com.sun.sgs.kernel;

import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import java.util.Properties;

/**
 * A transport manager.
 */
public interface TransportManager {

    /**
     * Start a new transport.
     * The transport name must resolve to a class that implements
     * {@link Transport}. The class should be public, not abstract, and should
     * provide a public constructor with a {@link Properties} and
     * {@link ConnectionHandler} parameter. The newly created transport is
     * returned and each call will return a new instance.
     * 
     * @param transportClassName name of the class that implements
     * {@link Transport}
     * @param properties properties passed to the transport's constructor
     * @param handler handler passed to the transport's constructor
     * @return the transport object
     * @throws IllegalArgumentException if any argument is {@code null} or if
     * the class specified by {@code transportClassName} does not implement
     * {@link Transport}
     * @throws Exception thrown from the transport's constructor
     */
    Transport startTransport(String transportClassName,
                             Properties properties,
                             ConnectionHandler handler) throws Exception;

}
