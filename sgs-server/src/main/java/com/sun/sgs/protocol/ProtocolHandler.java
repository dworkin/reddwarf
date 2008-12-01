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
 * A handler for protocol messages for an associated client session.
 *
 * Each operation has a {@code CompletionFuture} argument that, if
 * non-null, must be notified when the corresponding operation has been
 * processed.  A caller may require notification of operation completion
 * so that it can perform some throttling, for example only resuming
 * reading when a protocol message has been processed by the handler, or
 * controlling the number of clients connected at any given time.
 *
 * <p>TBD: should reconnection be handled a this layer or transparently by
 * the transport layer?
 *
 * <p>TBD: should a future be returned by the handler's methods instead of
 * supplied to them?
 */
public interface ProtocolHandler {
}
