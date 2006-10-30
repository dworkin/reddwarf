package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
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

    /** Map of all channels, by name. */
    private final Map<String, ManagedReference<ChannelState>> table =
	new HashMap<String, ManagedReference<ChannelState>>();

    /**
     * Constructs an instance of this class with an empty table.
     */
    ChannelTable() {}

    /**
     * Adds to this channel table a mapping from the specified channel
     * name to the specified managed reference to channel state.
     */
    void put(String name, ManagedReference<ChannelState> ref) {
	table.put(name, ref);
    }

    /**
     * Returns the managed reference to channel state for the
     * specified channel name.
     */
    ManagedReference<ChannelState> get(String name) {
	return table.get(name);
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
