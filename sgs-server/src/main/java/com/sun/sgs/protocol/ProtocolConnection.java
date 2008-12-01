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

import java.nio.channels.Channel;


/**
 * A channel for sending protocol messages to a client.  A {@code
 * ProtocolConnection} is created by the {@link
 * ProtocolFactory#newProtocol ProtocolFactory.newProtocol} method.
 *
 * <p>Note: If the protocol specification (implemented by a given {@code
 * ProtocolConnection}) requires that a login acknowledgment be
 * delivered to the client before any other protocol messages, the protocol
 * message channel must implement this requirement.  It is possible that a
 * caller may request that other messages be sent before the login
 * acknowledgment, and if the protocol requires, these messages should be
 * enqueued until the login acknowledgment has been sent to the client.
 * 
 * <p>TBD: should reconnection be handled a this layer or transparently by
 * the transport layer?   Perhaps the {@code AsynchronousByteChannel}
 * managed by the transport layer could handle the reconnection under the
 * covers?
 */
public interface ProtocolConnection extends Channel {
}
