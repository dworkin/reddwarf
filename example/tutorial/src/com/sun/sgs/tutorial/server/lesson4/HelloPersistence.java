/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.tutorial.server.lesson4;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

/**
 * A simple persistence example for the Sun Game Server.
 * As a {@link ManagedObject}, it is able to modify instance fields,
 * demonstrated here by tracking the last timestamp at which a task
 * was run and displaying the time delta.
 */
public class HelloPersistence
    implements AppListener,  // to get called during application startup.
               Serializable, // since all AppListeners are ManagedObjects.
               Task          // to schedule future calls to our run() method.
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(HelloPersistence.class.getName());

    /** The delay before the first run of the task. */
    public static final int DELAY_MS = 5000;

    /** The time to wait before repeating the task. */
    public static final int PERIOD_MS = 500;

    /**  The timestamp when this task was last run. */
    private long lastTimestamp = System.currentTimeMillis();

    // implement AppListener

    /**
     * {@inheritDoc}
     * <p>
     * Schedules the {@code run()} method to be called periodically.
     * Since SGS tasks are persistent, the scheduling only needs to
     * be done the first time the application is started.  When the
     * server is killed and restarted, the scheduled timer task will
     * continue ticking.
     * <p>
     * Runs the task {@value #DELAY_MS} ms from now,
     * repeating every {@value #PERIOD_MS} ms.
     */
    public void initialize(Properties props) {
        TaskManager taskManager = AppContext.getTaskManager();
        taskManager.schedulePeriodicTask(this, DELAY_MS, PERIOD_MS);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Prevents client logins by returning {@code null}.
     */
    public ClientSessionListener loggedIn(ClientSession session) {
        return null;
    }

    // implement Task

    /**
     * {@inheritDoc}
     * <p>
     * Each time this {@code Task} is run, logs the current timestamp and
     * the delta from the timestamp of the previous run.
     */
    public void run() throws Exception {
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
