/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.kernel;

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
 * all implementations of <code>ProfileOperationListener</code> must
 * implement a constructor of the form (<code>java.util.Properties</code>,
 * <code>com.sun.sgs.kernel.TaskOwner</code>,
 * <code>com.sun.sgs.kernel.TaskScheduler</code>,
 * <code>com.sun.sgs.kernel.ResourceCoordinator</code>).
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
public interface ProfileOperationListener {

    /**
     * Notifies this listener of a new change in the system
     * properties.  This method is called for any property that
     * changes.
     *
     * @param event A <code>PropertyChangeEvent</code> object
     *        describing the name of the property, its old and new
     *        values and the source of the change.
     */
    public void propertyChange(PropertyChangeEvent event);

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
    public void report(ProfileReport profileReport);

    /**
     * Tells this listener that the system is shutting down.
     */
    public void shutdown();

}
