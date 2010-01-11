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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.profile;

import java.beans.PropertyChangeEvent;


/**
 * This interface is used to listen for profiling data as reported by
 * the system. Unlike the individual operations provided to
 * <code>ProfileConsumer</code>, the data provided here is aggregated
 * data representing events in the scheduler or collected data about a
 * complete task run through the scheduler.  Implementaions of this
 * class will only be called within a single-threaded context, so
 * implementations do not need to be concurrent.
 *
 * <p>
 *
 * In order to create listeners with all of the facilities that they need,
 * all implementations of <code>ProfileListener</code> must
 * implement a constructor of the form (<code>java.util.Properties</code>,
 * <code>com.sun.sgs.auth.Identity</code>,
 * <code>com.sun.sgs.kernel.ComponentRegistry</code>).
 *
 * <p>
 *
 * Note that this interface is not complete. It is provided as an initial
 * attempt to capture basic aspects of operation. As more profiling and
 * investigation is done on the system, expect to see the information
 * provided here evolve.
 *
 * @see ProfileReport
 */
public interface ProfileListener {

    /**
     * The property for defining the number task to summarize for
     * windowed {@code com.sun.sgs.profile.ProfileListener}
     * implementations.  The value assigned to the property must be an
     * integer.
     */
    String WINDOW_SIZE_PROPERTY = "com.sun.sgs.profile.listener.window.size";
    
    /**
     * Notifies this listener of a new change in the system
     * properties.  This method is called for any property that
     * changes.
     *
     * <p>
     *
     * The current list of supported property names is as follows:
     *
     * <table border="0" style="font-size:80%" cellpadding="20%">
     * <tr>
     *   <td><b>name</b></td>
     *   <td><b>description</b></td>
     *   <td><b>new value</b></td>
     *   <td><b>old value</b></td>
     * </tr>
     * <tr>
     *   <td><code>com.sun.sgs.profile.newop</code></td>
     *   <td>a new operation is registered with the system.</td>
     *   <td>the new operation : 
     *          {@link com.sun.sgs.profile.ProfileOperation}</td>
     *   <td> <code>null</code></td>
     * </tr>
     * <tr>
     *   <td><code>com.sun.sgs.profile.threadcount</code></td>
     *   <td>the number of threads in the system has changed.</td>
     *   <td>the current number of threads : <code>Integer</code></td>
     *   <td>the previous number of threads : <code>Integer</code></td>
     * </tr>
     * <tr>
     *   <td><code>com.sun.sgs.profile.nodeid</code></td>
     *   <td>the local node has been assigned a unique identifier</td>
     *   <td>the identifier for the local node: <code>Long</code></td>
     *   <td><code>null</code></td>
     * </tr>
     *
     * </table>
     *
     * @param event A <code>PropertyChangeEvent</code> object
     *        describing the name of the property, its old and new
     *        values and the source of the change.
     */
    void propertyChange(PropertyChangeEvent event);

    /**
     * Reports a completed task that has been run through the scheduler. The
     * task may have completed successfully or may have failed. If a
     * task is re-tried, then this method will be called multiple times for
     * each re-try of the same task. Note that in this case the
     * <code>scheduledStartTime</code> will remain constant but the
     * <code>actualStartTime</code> will change for each re-try of the
     * same task.
     *
     * @param profileReport the <code>ProfileReport</code> for the task
     */
    void report(ProfileReport profileReport);

    /**
     * Tells this listener that the system is shutting down.
     */
    void shutdown();

}
