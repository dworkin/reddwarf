/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.NameNotBoundException;

/**
 * Provides static access to the current turn, which is stored in the data store.
 */
public class TurnManager {
    /** The data store binding for the singleton instance. */
    private static final String DATA_BINDING = "turn";

    /**
     * Creates a new {@code TurnManager}.
     */
    private TurnManager() {
        // empty
    }

    /**
     * Returns the singleton instance of the current turn, fetching it from the
     * data store first if necessary.
     */
    public static long getCurrentTurn() {
        return getTurnObject().get();
    }

    /**
     * Increments the singleton instance of the current turn, returning the new
     * value.  This should generally be called only by the task in charge of
     * turn timing). 
     */
    public static long incrementTurn() {
        ManagedLong turnObj = getTurnObject();
        AppContext.getDataManager().markForUpdate(turnObj);
        return turnObj.increment();
    }

    /**
     * Returns the {@link ManagedObject} that represents the current turn.
     */
    private static ManagedLong getTurnObject() {
        DataManager dm = AppContext.getDataManager();

        try {
            return dm.getBinding(DATA_BINDING, ManagedLong.class);
        } catch (NameNotBoundException nnbe) {
            /** Hasn't been created yet (first access). */
            ManagedLong turnObj = new ManagedLong(1);
            dm.setBinding(DATA_BINDING, turnObj);
            return turnObj;
        }
    }
}
