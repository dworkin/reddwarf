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

/**
 * Disclaimer: This code is purely experimental and should be taken as such.
 * It purpose is for entertainment value only.</b>
 * <p>
 *
 * This package contains two components. First it contains an implementation
 * of the protocol layer APIs using the that wraps the
 * {@code SimpleSgsProtocol} implementation (found in the
 * {@code com.sun.sgs.impl.protocol.simple} package). Second, it contains
 * the implementation of a custom manager/service pair that, in conjunction
 * with the protocol wrapper, provides the application with the IP address of
 * the connected client. The manager implements the interface:
 * {@link com.sun.sgs.app.IPManager} is in the {@code sgs-server-api} project.
 * <p>
 *
 * In order to use the manager, one must install both the wrapped protocol and
 * the manager/service pair. This can be done by adding the following lines
 * to the server's configuration file:<p>
 * <pre>
 * {@code
 * com.sun.sgs.impl.service.session.protocol.acceptor=\
 *                     com.sun.sgs.impl.protocol.ipwrapper.IPProtocolAcceptor
 *
 * com.sun.sgs.managers=com.sun.sgs.impl.protocol.ipwrapper.IPProtocolManager
 * com.sun.sgs.services=com.sun.sgs.impl.protocol.ipwrapper.IPProtocolService
 * }
 * </pre>
 * Note that if you already are installing a custom manager/service simply
 * add the new ones to the list.<p>
 *
 * When installed, the application can access the manager using the following:
 * <p>
 * <code>
 * IPManager ipm = AppContext.getManager(IPManager.class);
 * </code>
 */ 
package com.sun.sgs.impl.protocol.ipwrapper;
