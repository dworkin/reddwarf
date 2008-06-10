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

package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * A wrapper for a {@code ClientSessionImpl} object that is passed to the
 * application in the {@code AppListener.loggedIn} method when a client
 * session logs in.  A {@code ClientSessionWrapper} is passed to the
 * application instead of a direct reference to a {@code ClientSessionImpl}
 * instance to avoid the possibility of the application removing the {@code
 * ClientSessionImpl} instance from the data service and interfering with
 * the client session service's persistent data.
 *
 * <p>When a {@code ClientSessionWrapper} instance is removed from the data
 * service, the underlying client session is disconnected.
 */
public class ClientSessionWrapper
    implements ClientSession, Serializable, ManagedObjectRemoval
{
    /** The serialVersionUID for this class. */
    private final static long serialVersionUID = 1L;

    /** The reference to the client session that this instance wraps. */
    private final ManagedReference<ClientSessionImpl> sessionRef;

    /**
     * Constructs an instance with the specified {@code sessionRef}.
     *
     * @param	sessionRef a reference to a client session to wrap
     */
    ClientSessionWrapper(ManagedReference<ClientSessionImpl> sessionRef) {
	this.sessionRef = sessionRef;
    }

    /* -- Implement ClientSession -- */

    /** {@inheritDoc} */
    public String getName() {
	return getClientSession().getName();
    }

    /** {@inheritDoc} */
    public boolean isConnected() {
	try {
	    return sessionRef.get().isConnected();
	} catch (ObjectNotFoundException e) {
	    return false;
	}
    }

    /** {@inheritDoc} */
    public ClientSession send(ByteBuffer message) {
	getClientSession().send(message);
	return this;
    }

    /* -- Implement ManagedObjectRemoval -- */

    /** {@inheritDoc} */
    public void removingObject() {
	try {
	    sessionRef.get().disconnect();
	} catch (ObjectNotFoundException e) {
	    // already disconnected.
	}
    }

    /* -- Implement Object -- */

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object object) {
	if (object == this) {
	    return true;
	} else if (object instanceof ClientSessionWrapper) {
	    return sessionRef.equals(((ClientSessionWrapper) object).sessionRef);
	} else {
	    return false;
	}
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
	return sessionRef.hashCode();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
	ClientSessionImpl sessionImpl = null;
	try {
	    sessionImpl = sessionRef.get();
	} catch (ObjectNotFoundException e) {
	}
	return getClass().getName() + "[" +
	    (sessionImpl == null ? "(not found)" : sessionImpl.toString()) + "]";
    }
    
    /* -- Other methods -- */

    /**
     * Returns the underlying {@code ClientSessionImpl} instance for this
     * wrapper.  If the underlying client session has been removed, then
     * the client session has been disconnected, so {@code
     * IllegalStateException} is thrown.
     *
     * @return the underlying {@code ClientSessionImpl} instance for this
     * wrapper
     */
    public ClientSessionImpl getClientSession() {
	try {
	    return sessionRef.get();
	} catch (ObjectNotFoundException e) {
	    throw new IllegalStateException("client session is disconnected");
	}
    }
}
