/*
 * Copyright 2008 Sun Microsystems, Inc.
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
import com.sun.sgs.app.ManagedReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Table containing a map of all channels, by name.
 */
class ChannelTable implements ManagedObject, Serializable {

    /** Name of channel table in the data store. */
    static final String NAME = "channel.table";

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** Map of all channels, from name to managed reference for
     * ChannelState.
     */
    private final Map<String, ManagedReference> table =
	new HashMap<String, ManagedReference>();

    /**
     * Constructs an instance of this class with an empty table.
     */
    ChannelTable() {}

    /**
     * Adds to this channel table a mapping from the specified channel
     * name to the specified managed reference to channel state.
     */
    void put(String name, ManagedReference ref) {
	table.put(name, ref);
    }

    /**
     * Returns the managed reference to channel state for the
     * specified channel name.
     */
    ManagedReference get(String name) {
	return table.get(name);
    }

    /**
     * Returns a collection containing all managed references to
     * channel state that are in the table.
     */
    Collection<ManagedReference> getAll() {
	return table.values();
    }

    /**
     * Removes from this channel table the mapping for the specified
     * channel name to its associated channel state.
     */
    void remove(String name) {
	table.remove(name);
    }

    /* -- Serialization methods -- */

    private void writeObject(ObjectOutputStream out)
	throws IOException
    {
	out.defaultWriteObject();
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
