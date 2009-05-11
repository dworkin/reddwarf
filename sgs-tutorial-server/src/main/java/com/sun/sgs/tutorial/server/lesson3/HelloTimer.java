/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.tutorial.server.lesson3;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

/**
 * A simple timed-task example for the Project Darkstar Server.
 * It uses the {@link TaskManager} to schedule itself as a periodic task
 * that logs the current timestamp on each execution.
 */
public class HelloTimer
    implements AppListener,  // to get called during application startup.
               Serializable, // since all AppListeners are ManagedObjects.
               Task          // to schedule future calls to our run() method.
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(HelloTimer.class.getName());

    /** The delay before the first run of the task. */
    public static final int DELAY_MS = 5000;

    /** The time to wait before repeating the task. */
    public static final int PERIOD_MS = 500;

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
     * Logs the current timestamp whenever this {@code Task} gets run.
     */
    public void run() throws Exception {
        logger.log(Level.INFO,
            "HelloTimer task: running at timestamp {0,number,#}",
            System.currentTimeMillis());
    }
}
