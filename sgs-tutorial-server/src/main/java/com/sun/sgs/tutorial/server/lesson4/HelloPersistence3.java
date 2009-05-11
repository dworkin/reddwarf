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

package com.sun.sgs.tutorial.server.lesson4;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

/**
 * A simple persistence example for the Project Darkstar Server.
 */
public class HelloPersistence3
    implements AppListener, Serializable, Task
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(HelloPersistence3.class.getName());

    /** The delay before the first run of the task. */
    public static final int DELAY_MS = 5000;

    /** The time to wait before repeating the task. */
    public static final int PERIOD_MS = 500;

    /** A reference to our subtask, a {@link TrivialTimedTask}.  */
    private ManagedReference<TrivialTimedTask> subTaskRef = null;

    /**
     * Gets the subtask this task delegates to.  Dereferences a
     * {@link ManagedReference} in this object that holds the subtask.
     * <p>
     * This null-check idiom is common when getting a ManagedReference.
     *
     * @return the subtask this task delegates to, or null if none is set
     */
    public TrivialTimedTask getSubTask() {
        if (subTaskRef == null) {
            return null;
        }

        return subTaskRef.get();
    }

    /**
     * Sets the subtask this task delegates to.  Stores the subtask
     * as a {@link ManagedReference} in this object.
     * <p>
     * This null-check idiom is common when setting a ManagedReference,
     * since {@link DataManager#createReference createReference} does
     * not accept null parameters.
     *
     * @param subTask the subtask this task should delegate to,
     *        or null to clear the subtask
     */
    public void setSubTask(TrivialTimedTask subTask) {
        if (subTask == null) {
            subTaskRef = null;
            return;
        }
        DataManager dataManager = AppContext.getDataManager();
        subTaskRef = dataManager.createReference(subTask);
    }

    // implement AppListener

    /**
     * {@inheritDoc}
     * <p>
     * Schedules the {@code run()} method of this object to be called
     * periodically.
     * <p>
     * Since SGS tasks are persistent, the scheduling only needs to
     * be done the first time the application is started.  When the
     * server is killed and restarted, the scheduled timer task will
     * continue ticking.
     * <p>
     * Runs the task {@value #DELAY_MS} ms from now,
     * repeating every {@value #PERIOD_MS} ms.
     */
    public void initialize(Properties props) {
        // Hold onto the task (as a managed reference)
        setSubTask(new TrivialTimedTask());

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
     * Calls the run() method of the subtask set on this object.
     */
    public void run() throws Exception {
        // Get the subTask (from the ManagedReference that holds it)
        TrivialTimedTask subTask = getSubTask();

        if (subTask == null) {
            logger.log(Level.WARNING, "subTask is null");
            return;
        }

        // Delegate to the subTask's run() method
        subTask.run();
    }
}
