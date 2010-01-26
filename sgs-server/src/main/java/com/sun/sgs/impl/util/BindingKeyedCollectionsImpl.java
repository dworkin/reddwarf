/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TransactionProxy;

/**
 * An implementation of the collections factory.  The kernel constructs
 * this factory during server initialization, and it is available to
 * services via the {@link ComponentRegistry}.
 */
public class BindingKeyedCollectionsImpl implements BindingKeyedCollections {

    /** The transaction proxy, or null if the constructor hasn't been called. */
    private static TransactionProxy txnProxy;

    /**
     * Constructs an instance with the specified transaction proxy.
     *
     * @param	txnProxy the transaction proxy
     */
    public BindingKeyedCollectionsImpl(TransactionProxy txnProxy) {
	assert txnProxy != null;
	synchronized (BindingKeyedCollectionsImpl.class) {
	    BindingKeyedCollectionsImpl.txnProxy = txnProxy;
	}
    }

    /** {@inheritDoc} */
    public <V> BindingKeyedMap<V> newMap(String keyPrefix) {
	return new BindingKeyedMapImpl<V>(keyPrefix);
    }

    /** {@inheritDoc} */
    public <V> BindingKeyedSet<V> newSet(String keyPrefix) {
	return new BindingKeyedSetImpl<V>(keyPrefix);
    }

    /**
     * Returns the data service relevant to the current context. This
     * method is used by {@link BindingKeyedMapImpl} to obtain the data
     * service to store key/value pairs.
     *
     * @return	the data service relevant to the current context
     * @throws	IllegalStateException if an instance of this class has not
     *		been initialized
     */
    static synchronized DataService getDataService() {
	if (txnProxy == null) {
	    throw new IllegalStateException(
		"BindingKeyedCollections not initialized");
	} else {
	    return txnProxy.getService(DataService.class);
	}
    }
}
