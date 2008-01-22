/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.ManagedObject;
import java.io.Serializable;

/**
 * A {@code ManagedObject} wrapper for a {@code ChannelServer}.
 *
 * TBD: unexport the server somehow if the wrapper gets removed from the
 * data service?
 */
public class ChannelServerWrapper implements ManagedObject, Serializable {

    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The channel server. */
    private ChannelServer server;

    /**
     * Constructs a wrapper for the specified {@code server}.
     *
     * @param	server a channel server
     */
    ChannelServerWrapper(ChannelServer server) {
	if (server == null) {
	    throw new NullPointerException("null server");
	}
	this.server = server;
    }

    /**
     * Returns the channel server that this instance wraps.
     *
     * @return	the channel server that this instance wraps
     */
    ChannelServer get() {
	return server;
    }
}

