/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.Location;

/**
 * Provides static access to an instance of {@link World}, which is backed by
 * the data store (so that JVMs on different nodes can all load an instance of
 * this class, backed by the same, single instance in the data store).
 */
public class WorldManager {
    /** The data store binding for the singleton instance. */
    private static final String DATA_BINDING = "world";

    /** The singleton {@link World} instance. */
    private static World instance = null;

    /**
     * Creates a new {@code WorldManager}.
     */
    private WorldManager() {
        // empty
    }

    /**
     * Returns the singleton instance of {@link World}, fetching it from the
     * data store first if necessary.
     *
     * @throws IllegalStateException if the object does not exist in the data
     *         store, implying that this method was called before initialize()
     *         was called (on any node).
     */
    public static World getWorld() {
        if (instance == null) {
            try {
                DataManager dm = AppContext.getDataManager();
                instance = dm.getBinding(DATA_BINDING, World.class);
            } catch (NameNotBoundException nnbe) {
                throw new IllegalStateException("getInstance() called before" +
                    " initialize().");
            }
        }

        return instance;
    }

    /**
     * Loads a new {@link World} instance into the data store.
     */
    public static void initialize(World world) {
        DataManager dm = AppContext.getDataManager();
        dm.setBinding(DATA_BINDING, world);
        instance = world;
    }
}
