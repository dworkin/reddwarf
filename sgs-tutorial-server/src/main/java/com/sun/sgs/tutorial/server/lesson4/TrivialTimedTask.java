/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.tutorial.server.lesson4;

import com.sun.sgs.app.AppContext;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.Task;

/**
 * A simple repeating Task that tracks and prints the time since it was
 * last run.
 */
public class TrivialTimedTask
    implements Serializable,  // for persistence, as required by ManagedObject.
               ManagedObject, // to let the SGS manage our persistence.
               Task           // to schedule future calls to our run() method.
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(TrivialTimedTask.class.getName());

    /**  The timestamp when this task was last run. */
    private long lastTimestamp = System.currentTimeMillis();

    // implement Task

    /**
     * {@inheritDoc}
     * <p>
     * Each time this {@code Task} is run, logs the current timestamp and
     * the delta from the timestamp of the previous run.
     */
    public void run() throws Exception {
        // We will be modifying this object.
        AppContext.getDataManager().markForUpdate(this);
        long timestamp = System.currentTimeMillis();
        long delta = timestamp - lastTimestamp;

        // Update the field holding the most recent timestamp.
        lastTimestamp = timestamp;

        logger.log(Level.INFO,
            "timestamp = {0,number,#}, delta = {1,number,#}",
            new Object[] { timestamp, delta }
        );
    }
}
